package com.dynablox.launcher.service

import android.app.Service
import android.content.Intent
import android.graphics.Point
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.dynablox.launcher.DynabloxApp
import com.dynablox.launcher.R
import com.dynablox.launcher.controls.ClusterSide
import com.dynablox.launcher.controls.InputInjector
import com.dynablox.launcher.controls.PadButton
import com.dynablox.launcher.controls.PadClusterView
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.notify.Notifications
import com.dynablox.launcher.core.overlay.ScreenMetrics
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.di.AppContainer
import com.dynablox.launcher.ui.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Floating virtual controls.
 *
 * Two windows — one per thumb cluster — sized exactly to their keys, so the rest of the screen stays
 * fully playable. A key press is injected for real: `input tap/swipe/keyevent` through Shizuku when
 * the shell identity is available, `AccessibilityService.dispatchGesture` otherwise. When neither is
 * granted the pad says so instead of pretending.
 */
class ControlsService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val clusters = mutableMapOf<ClusterSide, PadClusterView>()
    private val params = mutableMapOf<ClusterSide, WindowManager.LayoutParams>()
    private var observeJob: Job? = null
    private var latencyJob: Job? = null
    private var screen: ScreenMetrics = ScreenMetrics(0, 0, 0, 0)

    override fun onCreate() {
        super.onCreate()
        container = DynabloxApp.containerOf(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE, ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> if (clusters.isNotEmpty()) {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            } else {
                show()
            }

            ACTION_REFRESH -> {
                rebuild()
                reposition()
            }

            ACTION_TEST -> testInjection()

            else -> show()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun show() {
        if (clusters.isNotEmpty()) return
        if (!container.windows.canDrawOverlays()) {
            container.toastFeedback.show(getString(R.string.msg_needs_overlay), CommandFeedback.Kind.WARNING)
            stopSelf()
            return
        }
        screen = container.windows.metrics()

        val prepared = mutableListOf<Triple<ClusterSide, PadClusterView, WindowManager.LayoutParams>>()
        listOf(ClusterSide.LEFT, ClusterSide.RIGHT).forEach { side ->
            val view = PadClusterView(this, container, side)
            view.onKey = { button -> inject(button) }
            view.setOnTouchListener(dragListener(side))
            val (width, height) = view.desiredSize()
            val position = anchorPosition(side, width, height, screen)
            prepared += Triple(side, view, container.windows.layoutParams(width, height, position.x, position.y))
        }

        isRunning = true
        startForeground(Notifications.ID_CONTROLS, notification(null))
        try {
            prepared.forEach { (side, view, layoutParams) ->
                container.windows.addView(view, layoutParams)
                clusters[side] = view
                params[side] = layoutParams
            }
        } catch (t: Throwable) {
            prepared.forEach { (_, view, _) -> view.release() }
            teardown()
            stopSelf()
            return
        }
        observeSettings()
        observeLatency()

        if (!container.inputReady()) {
            container.toastFeedback.show(getString(R.string.ctrl_needs_backend), CommandFeedback.Kind.WARNING)
        }
    }

    private fun observeSettings() {
        observeJob?.cancel()
        observeJob = scope.launch {
            container.settings.changes.collect {
                rebuild()
                reposition()
            }
        }
    }

    private fun observeLatency() {
        latencyJob?.cancel()
        latencyJob = scope.launch {
            container.injector.latency.collect { latency ->
                if (latency > 0f) {
                    container.notifications.notify(
                        Notifications.ID_CONTROLS,
                        notification(latency),
                    )
                }
            }
        }
    }

    private fun rebuild() {
        clusters.values.forEach { view ->
            view.rebuild()
            view.applyOpacity()
            view.refreshTheme()
        }
    }

    private fun reposition() {
        screen = container.windows.metrics()
        clusters.forEach { (side, view) ->
            val (width, height) = view.desiredSize()
            val current = params[side] ?: return@forEach
            current.width = width
            current.height = height
            val position = anchorPosition(side, width, height, screen)
            current.x = position.x
            current.y = position.y
            container.windows.updateView(view, current)
        }
    }

    private fun anchorPosition(
        side: ClusterSide,
        width: Int,
        height: Int,
        metrics: ScreenMetrics,
    ): Point {
        val settings = container.settings
        val margin = Dimens.dp(this, 8f).toInt()
        val anchorX = if (side == ClusterSide.LEFT) settings.controlsLeftAnchorX else settings.controlsRightAnchorX
        val anchorY = if (side == ClusterSide.LEFT) settings.controlsLeftAnchorY else settings.controlsRightAnchorY
        val x = (metrics.widthPx * anchorX.coerceIn(0f, 1f)).toInt()
        val y = (metrics.statusBarPx + metrics.usableHeight * anchorY.coerceIn(0f, 1f)).toInt()
        return Point(
            (x - width / 2).coerceIn(margin, max(margin, metrics.widthPx - width - margin)),
            (y - height / 2).coerceIn(metrics.statusBarPx + margin, max(1, metrics.heightPx - height - margin)),
        )
    }

    private fun dragListener(side: ClusterSide): View.OnTouchListener {
        var startX = 0f
        var startY = 0f
        var dragging = false
        val threshold = Dimens.dp(this, 10f)
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    dragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val current = params[side]
                    val view = clusters[side]
                    if (current == null || view == null) {
                        false
                    } else {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        if (!dragging && (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)) {
                            dragging = true
                        }
                        if (dragging) {
                            current.x = (current.x + dx.toInt())
                                .coerceIn(0, max(0, screen.widthPx - current.width))
                            current.y = (current.y + dy.toInt())
                                .coerceIn(0, max(0, screen.heightPx - current.height))
                            container.windows.updateView(view, current)
                            startX = event.rawX
                            startY = event.rawY
                            true
                        } else {
                            false
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val current = params[side]
                    val wasDragging = dragging
                    dragging = false
                    if (wasDragging && current != null) {
                        persistAnchor(side, current)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    false
                }

                else -> false
            }
        }
    }

    private fun persistAnchor(side: ClusterSide, current: WindowManager.LayoutParams) {
        val settings = container.settings
        val centerX = (current.x + current.width / 2).toFloat() / max(1, screen.widthPx)
        val centerY = (current.y + current.height / 2 - screen.statusBarPx).toFloat() / max(1, screen.usableHeight)
        if (side == ClusterSide.LEFT) {
            settings.controlsLeftAnchorX = centerX.coerceIn(0.02f, 0.98f)
            settings.controlsLeftAnchorY = centerY.coerceIn(0.02f, 0.98f)
        } else {
            settings.controlsRightAnchorX = centerX.coerceIn(0.02f, 0.98f)
            settings.controlsRightAnchorY = centerY.coerceIn(0.02f, 0.98f)
        }
    }

    private fun inject(button: PadButton) {
        if (!container.inputReady()) {
            container.toastFeedback.show(getString(R.string.ctrl_needs_backend), CommandFeedback.Kind.ERROR)
            return
        }
        scope.launch {
            val metrics = container.windows.metrics()
            val result = container.injector.gesture(
                button.gesture, button.targetX, button.targetY,
                metrics.widthPx, metrics.heightPx,
            )
            if (!result.success) {
                container.toastFeedback.show(
                    result.message ?: getString(R.string.ctrl_injection_failed),
                    CommandFeedback.Kind.ERROR,
                )
            }
        }
    }

    /** Injects a single tap in the middle of the screen so the user can verify the backend. */
    private fun testInjection() {
        scope.launch {
            val metrics = container.windows.metrics()
            val result = container.injector.tap(metrics.widthPx / 2f, metrics.heightPx / 2f)
            val kind = if (result.success) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.ERROR
            container.toastFeedback.show(
                getString(
                    R.string.ctrl_test_result,
                    methodLabel(result.method),
                    Fmt.number(result.latencyMs.toFloat(), 0),
                ),
                kind,
            )
        }
    }

    private fun methodLabel(method: InputInjector.Method): String = getString(
        when (method) {
            InputInjector.Method.SHIZUKU -> R.string.ctrl_method_shizuku
            InputInjector.Method.ACCESSIBILITY -> R.string.ctrl_method_accessibility
            InputInjector.Method.NONE -> R.string.ctrl_method_none
        },
    )

    private fun notification(latency: Float?) = container.notifications.build(
        channelId = Notifications.CHANNEL_OVERLAY,
        title = getString(R.string.notif_controls_title),
        text = getString(
            R.string.notif_controls_text,
            methodLabel(container.injector.activeMethod()),
            if (latency != null && latency > 0f) Fmt.number(latency, 0) else "--",
        ),
        contentIntent = container.notifications.pendingActivity(
            Intent(this, LauncherActivity::class.java)
                .putExtra(LauncherActivity.EXTRA_OPEN_CONTROLS, true), 41,
        ),
        actions = listOf(
            getString(R.string.notif_action_test) to
                container.notifications.pendingService(ControlsService::class.java, ACTION_TEST, 42),
            getString(R.string.notif_action_hide) to
                container.notifications.pendingService(ControlsService::class.java, ACTION_HIDE, 43),
        ),
    )

    private fun teardown() {
        observeJob?.cancel()
        latencyJob?.cancel()
        observeJob = null
        latencyJob = null
        clusters.forEach { (side, view) ->
            view.release()
            container.windows.removeView(view)
            params.remove(side)
        }
        clusters.clear()
        isRunning = false
    }

    companion object {
        const val ACTION_SHOW = "com.dynablox.launcher.controls.SHOW"
        const val ACTION_HIDE = "com.dynablox.launcher.controls.HIDE"
        const val ACTION_STOP = "com.dynablox.launcher.controls.STOP"
        const val ACTION_TOGGLE = "com.dynablox.launcher.controls.TOGGLE"
        const val ACTION_REFRESH = "com.dynablox.launcher.controls.REFRESH"
        const val ACTION_TEST = "com.dynablox.launcher.controls.TEST"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

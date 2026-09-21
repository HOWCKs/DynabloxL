package com.dynablox.launcher.service

import android.app.Service
import android.content.Intent
import android.graphics.Point
import android.os.IBinder
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.dynablox.launcher.DynabloxApp
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.notify.Notifications
import com.dynablox.launcher.core.overlay.ScreenMetrics
import com.dynablox.launcher.core.perf.PerfSnapshot
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.di.AppContainer
import com.dynablox.launcher.hud.HudView
import com.dynablox.launcher.ui.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Floating performance monitor.
 *
 * Owns a [HudView] window, drives it from [com.dynablox.launcher.core.perf.PerfEngine]'s snapshot
 * flow and mirrors the live numbers into its foreground notification. Tap cycles mini / full / graph;
 * drag repositions (and the position is persisted).
 */
class PerfHudService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var hudView: HudView? = null
    private var params: WindowManager.LayoutParams? = null
    private var telemetryJob: Job? = null
    private var observeJob: Job? = null
    private var screen: ScreenMetrics = ScreenMetrics(0, 0, 0, 0)
    private var hintSession: Any? = null

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

            ACTION_TOGGLE -> if (hudView != null) {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            } else {
                show()
            }

            ACTION_CYCLE_MODE -> cycleMode()

            ACTION_RESET -> hudView?.resetTrace()

            ACTION_REFRESH -> {
                hudView?.refreshTheme()
                reposition()
            }

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
        if (hudView != null) return
        if (!container.windows.canDrawOverlays()) {
            container.toastFeedback.show(getString(R.string.msg_needs_overlay), CommandFeedback.Kind.WARNING)
            stopSelf()
            return
        }

        val view = HudView(this, container)
        hudView = view
        screen = container.windows.metrics()
        view.setMode(container.settings.hudMode, rebuild = false)

        val width = Dimens.dp(this, HudView.widthDp(container.settings.hudMode)).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.AT_MOST),
            android.view.View.MeasureSpec.makeMeasureSpec(
                (screen.usableHeight * 0.6f).toInt(), android.view.View.MeasureSpec.AT_MOST,
            ),
        )
        val position = anchorPosition(view.measuredWidth, view.measuredHeight, screen)
        val layoutParams = container.windows.layoutParams(
            max(view.measuredWidth, width / 2), max(view.measuredHeight, Dimens.dp(this, 40f).toInt()),
            position.x, position.y,
        )
        params = layoutParams
        view.setOnTouchListener(dragListener())
        isRunning = true
        startForeground(Notifications.ID_PERF_HUD, notification(null))
        try {
            container.windows.addView(view, layoutParams)
        } catch (t: Throwable) {
            teardown()
            stopSelf()
            return
        }
        hintSession = container.openPerformanceHint()
        container.perf.start()
        collectTelemetry()
        observeSettings()
    }

    private fun collectTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var lastNotification = 0L
            container.perf.snapshot.collectLatest { snapshot ->
                hudView?.bind(snapshot)
                val now = System.currentTimeMillis()
                if (now - lastNotification > NOTIFICATION_INTERVAL_MS) {
                    lastNotification = now
                    container.notifications.notify(Notifications.ID_PERF_HUD, notification(snapshot))
                }
            }
        }
    }

    private fun observeSettings() {
        observeJob?.cancel()
        observeJob = scope.launch {
            container.settings.changes.collect {
                val view = hudView ?: return@collect
                view.setMode(container.settings.hudMode)
                view.refreshTheme()
                view.resetTrace()
                reposition()
            }
        }
    }

    private fun cycleMode() {
        val view = hudView ?: return
        val mode = view.cycleMode()
        reposition()
        container.toastFeedback.show(getString(R.string.hud_mode_changed, label(mode)), CommandFeedback.Kind.INFO)
        container.notifications.notify(Notifications.ID_PERF_HUD, notification(null))
    }

    private fun label(mode: AppSettings.HudMode): String = getString(
        when (mode) {
            AppSettings.HudMode.MINI -> R.string.hud_mode_mini
            AppSettings.HudMode.FULL -> R.string.hud_mode_full
            AppSettings.HudMode.GRAPH -> R.string.hud_mode_graph
        },
    )

    private fun reposition() {
        val view = hudView ?: return
        val current = params ?: return
        screen = container.windows.metrics()
        val width = Dimens.dp(this, HudView.widthDp(container.settings.hudMode)).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.AT_MOST),
            android.view.View.MeasureSpec.makeMeasureSpec(
                (screen.usableHeight * 0.6f).toInt(), android.view.View.MeasureSpec.AT_MOST,
            ),
        )
        current.width = max(view.measuredWidth, width / 2)
        current.height = max(view.measuredHeight, Dimens.dp(this, 40f).toInt())
        val position = anchorPosition(current.width, current.height, screen)
        current.x = position.x
        current.y = position.y
        container.windows.updateView(view, current)
    }

    private fun anchorPosition(width: Int, height: Int, metrics: ScreenMetrics): Point {
        val settings = container.settings
        val margin = Dimens.dp(this, settings.orbEdgeMarginDp.toFloat()).toInt()
        val x = (metrics.widthPx * settings.hudAnchorX.coerceIn(0f, 1f)).toInt()
        val y = (metrics.statusBarPx + metrics.usableHeight * settings.hudAnchorY.coerceIn(0f, 1f)).toInt()
        return Point(
            (x - width / 2).coerceIn(margin, max(margin, metrics.widthPx - width - margin)),
            y.coerceIn(metrics.statusBarPx + margin, max(1, metrics.heightPx - height - margin)),
        )
    }

    private fun dragListener(): android.view.View.OnTouchListener {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var dragging = false
        var downAt = 0L
        return android.view.View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    downAt = System.currentTimeMillis()
                    dragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val current = params
                        val view = hudView
                        if (current != null && view != null) {
                            current.x = (current.x + dx.toInt())
                                .coerceIn(0, max(0, screen.widthPx - current.width))
                            current.y = (current.y + dy.toInt())
                                .coerceIn(0, max(0, screen.heightPx - current.height))
                            container.windows.updateView(view, current)
                            startX = event.rawX
                            startY = event.rawY
                        }
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        dragging = false
                        persistAnchor()
                        true
                    } else if (System.currentTimeMillis() - downAt < ViewConfiguration.getLongPressTimeout()) {
                        // A tap cycles mini / full / graph.
                        cycleMode()
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

    private fun persistAnchor() {
        val current = params ?: return
        val settings = container.settings
        val centerX = current.x + current.width / 2
        settings.hudAnchorX = (centerX.toFloat() / max(1, screen.widthPx)).coerceIn(0.05f, 0.95f)
        settings.hudAnchorY = ((current.y - screen.statusBarPx).toFloat() / max(1, screen.usableHeight))
            .coerceIn(0.02f, 0.98f)
    }

    private fun notification(snapshot: PerfSnapshot?) = container.notifications.build(
        channelId = Notifications.CHANNEL_TELEMETRY,
        title = getString(R.string.notif_hud_title),
        text = if (snapshot == null) {
            getString(R.string.notif_hud_text_idle, label(container.settings.hudMode))
        } else {
            getString(
                R.string.notif_hud_text_live,
                Fmt.number(snapshot.fps, 0),
                Fmt.number(snapshot.cpuPercent, 0),
                snapshot.thermalLabel,
            )
        },
        contentIntent = container.notifications.pendingActivity(
            Intent(this, LauncherActivity::class.java), 31,
        ),
        actions = listOf(
            getString(R.string.notif_action_mode) to
                container.notifications.pendingService(PerfHudService::class.java, ACTION_CYCLE_MODE, 32),
            getString(R.string.notif_action_hide) to
                container.notifications.pendingService(PerfHudService::class.java, ACTION_HIDE, 33),
        ),
    )

    private fun teardown() {
        telemetryJob?.cancel()
        observeJob?.cancel()
        telemetryJob = null
        observeJob = null
        container.perf.stop()
        hintSession = null
        hudView?.let { container.windows.removeView(it) }
        hudView = null
        params = null
        isRunning = false
    }

    companion object {
        const val ACTION_SHOW = "com.dynablox.launcher.hud.SHOW"
        const val ACTION_HIDE = "com.dynablox.launcher.hud.HIDE"
        const val ACTION_STOP = "com.dynablox.launcher.hud.STOP"
        const val ACTION_TOGGLE = "com.dynablox.launcher.hud.TOGGLE"
        const val ACTION_CYCLE_MODE = "com.dynablox.launcher.hud.CYCLE_MODE"
        const val ACTION_RESET = "com.dynablox.launcher.hud.RESET"
        const val ACTION_REFRESH = "com.dynablox.launcher.hud.REFRESH"

        private const val NOTIFICATION_INTERVAL_MS = 4000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

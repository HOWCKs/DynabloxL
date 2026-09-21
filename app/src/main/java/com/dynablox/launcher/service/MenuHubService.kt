package com.dynablox.launcher.service

import android.app.Service
import android.content.Intent
import android.graphics.Point
import android.os.IBinder
import android.view.MotionEvent
import android.view.WindowManager
import com.dynablox.launcher.R
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.overlay.ScreenMetrics
import com.dynablox.launcher.core.notify.Notifications
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.di.AppContainer
import com.dynablox.launcher.menu.MenuHubView
import com.dynablox.launcher.ui.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Hosts the floating MenuHub: orb, radial arc and modular panel, in one window that is resized and
 * repositioned as the hub changes state.
 *
 * The window is only as large as the visible surface, so touches anywhere else reach the app below
 * and come back as [MotionEvent.ACTION_OUTSIDE] — which collapses the hub, exactly like a launcher
 * panel should.
 */
class MenuHubService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var hubView: MenuHubView? = null
    private var params: WindowManager.LayoutParams? = null
    private var observeJob: Job? = null
    private var screen: ScreenMetrics = ScreenMetrics(0, 0, 0, 0)

    override fun onCreate() {
        super.onCreate()
        container = com.dynablox.launcher.DynabloxApp.containerOf(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE, ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> if (hubView != null) {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            } else {
                show(openPanel = false)
            }

            ACTION_TOGGLE_PANEL -> if (hubView == null) show(openPanel = true) else togglePanel()

            ACTION_COLLAPSE -> hubView?.collapse()

            ACTION_REFRESH -> {
                hubView?.refreshTheme()
                reposition(animate = false)
            }

            else -> show(openPanel = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ window

    private fun show(openPanel: Boolean) {
        if (hubView != null) {
            if (openPanel) hubView?.openPanel()
            return
        }
        if (!container.windows.canDrawOverlays()) {
            container.toastFeedback.show(
                getString(R.string.msg_needs_overlay),
                CommandFeedback.Kind.WARNING,
            )
            stopSelf()
            return
        }

        val view = MenuHubView(this, container)
        hubView = view
        screen = container.windows.metrics()

        wire(view)
        if (openPanel) view.applyState(MenuHubView.State.PANEL, animate = false)

        val geometry = view.computeGeometry(screen)
        view.bindGeometry(geometry)
        val position = windowPosition(geometry, orbCenter(screen), screen)
        val layoutParams = container.windows.layoutParams(
            geometry.width, geometry.height, position.x, position.y,
            focusable = view.state == MenuHubView.State.PANEL,
        )
        params = layoutParams
        // The service was started with startForegroundService, so the notification goes up first:
        // if addView fails the process is never left with a foreground service and no window.
        isRunning = true
        startForeground(Notifications.ID_MENU_HUB, notification(view.state))
        try {
            container.windows.addView(view, layoutParams)
        } catch (t: Throwable) {
            teardown()
            stopSelf()
            return
        }
        observe()
    }

    private fun wire(view: MenuHubView) {
        view.onDrag = { dx, dy -> move(dx, dy) }
        view.onDragEnd = { persistAnchor() }
        view.onDismiss = {
            teardown()
            stopSelf()
        }
        view.onStateChange = { state ->
            reposition(animate = true)
            updateNotification(state)
        }
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                view.handleOutsideTouch()
                true
            } else {
                false
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = scope.launch {
            container.settings.changes.collect {
                hubView?.refreshTheme()
                reposition(animate = false)
            }
        }
    }

    private fun togglePanel() {
        val view = hubView ?: return
        if (view.state == MenuHubView.State.PANEL) {
            view.applyState(MenuHubView.State.ARC, animate = true)
        } else {
            view.openPanel()
        }
    }

    /** Recomputes the window rect for the current state and slides it into place. */
    private fun reposition(animate: Boolean) {
        val view = hubView ?: return
        val current = params ?: return
        screen = container.windows.metrics()
        val geometry = view.computeGeometry(screen)
        view.bindGeometry(geometry)

        val target = windowPosition(geometry, orbCenter(screen), screen)
        current.width = geometry.width
        current.height = geometry.height

        val focusable = view.state == MenuHubView.State.PANEL
        current.flags = if (focusable) {
            current.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            current.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        current.softInputMode = if (focusable) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        if (!animate || Motion.duration(160L) == 0L) {
            current.x = target.x
            current.y = target.y
            container.windows.updateView(view, current)
            return
        }

        val fromX = current.x
        val fromY = current.y
        val animation = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animation.duration = Motion.duration(220L)
        animation.interpolator = Motion.interpolator()
        animation.addUpdateListener {
            val fraction = it.animatedValue as Float
            current.x = (fromX + (target.x - fromX) * fraction).toInt()
            current.y = (fromY + (target.y - fromY) * fraction).toInt()
            container.windows.updateView(view, current)
        }
        animation.start()
    }

    private fun move(dx: Int, dy: Int) {
        val current = params ?: return
        val view = hubView ?: return
        current.x = (current.x + dx).coerceIn(0, max(0, screen.widthPx - current.width))
        current.y = (current.y + dy).coerceIn(0, max(0, screen.heightPx - current.height))
        container.windows.updateView(view, current)
    }

    /** Stores the orb centre as a normalised anchor so it survives rotation and restarts. */
    private fun persistAnchor() {
        val current = params ?: return
        val view = hubView ?: return
        val center = view.orbScreenCenter(current.x, current.y)
        val settings = container.settings
        settings.orbAnchorX = (center.x.toFloat() / max(1, screen.widthPx)).coerceIn(0.04f, 0.96f)
        settings.orbAnchorY = ((center.y - screen.statusBarPx).toFloat() / max(1, screen.usableHeight))
            .coerceIn(0.02f, 0.98f)
    }

    private fun orbCenter(metrics: ScreenMetrics): Point {
        val settings = container.settings
        val margin = Dimens.dp(this, settings.orbEdgeMarginDp.toFloat()).toInt()
        val x = (metrics.widthPx * settings.orbAnchorX.coerceIn(0f, 1f)).toInt()
        val y = (metrics.statusBarPx + metrics.usableHeight * settings.orbAnchorY.coerceIn(0f, 1f)).toInt()
        return Point(
            x.coerceIn(margin, max(margin, metrics.widthPx - margin)),
            y.coerceIn(metrics.statusBarPx + margin, max(1, metrics.heightPx - margin)),
        )
    }

    private fun windowPosition(
        geometry: MenuHubView.Geometry,
        orbCenter: Point,
        metrics: ScreenMetrics,
    ): Point {
        val left = orbCenter.x - geometry.orbX
        val top = orbCenter.y - geometry.orbY
        return Point(
            left.coerceIn(0, max(0, metrics.widthPx - geometry.width)),
            top.coerceIn(0, max(0, metrics.heightPx - geometry.height)),
        )
    }

    private fun notification(state: MenuHubView.State) = container.notifications.build(
        channelId = Notifications.CHANNEL_OVERLAY,
        title = getString(R.string.notif_hub_title),
        text = getString(
            when (state) {
                MenuHubView.State.COLLAPSED -> R.string.notif_hub_collapsed
                MenuHubView.State.ARC -> R.string.notif_hub_arc
                MenuHubView.State.PANEL -> R.string.notif_hub_panel
            },
        ),
        contentIntent = container.notifications.pendingActivity(
            Intent(this, LauncherActivity::class.java), 21,
        ),
        actions = listOf(
            getString(R.string.notif_action_panel) to
                container.notifications.pendingService(MenuHubService::class.java, ACTION_TOGGLE_PANEL, 22),
            getString(R.string.notif_action_hide) to
                container.notifications.pendingService(MenuHubService::class.java, ACTION_HIDE, 23),
        ),
    )

    private fun updateNotification(state: MenuHubView.State) {
        if (!isRunning) return
        container.notifications.notify(Notifications.ID_MENU_HUB, notification(state))
    }

    private fun teardown() {
        observeJob?.cancel()
        observeJob = null
        hubView?.let { view ->
            view.release()
            container.windows.removeView(view)
        }
        hubView = null
        params = null
        isRunning = false
    }

    companion object {
        const val ACTION_SHOW = "com.dynablox.launcher.hub.SHOW"
        const val ACTION_HIDE = "com.dynablox.launcher.hub.HIDE"
        const val ACTION_STOP = "com.dynablox.launcher.hub.STOP"
        const val ACTION_TOGGLE = "com.dynablox.launcher.hub.TOGGLE"
        const val ACTION_TOGGLE_PANEL = "com.dynablox.launcher.hub.TOGGLE_PANEL"
        const val ACTION_COLLAPSE = "com.dynablox.launcher.hub.COLLAPSE"
        const val ACTION_REFRESH = "com.dynablox.launcher.hub.REFRESH"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

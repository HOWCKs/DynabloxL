package com.dynablox.launcher.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import com.dynablox.launcher.DynabloxApp
import com.dynablox.launcher.R
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.notify.Notifications
import com.dynablox.launcher.di.AppContainer
import com.dynablox.launcher.fx.FxPresets
import com.dynablox.launcher.fx.FxView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Honest full-screen FX overlay.
 *
 * The window is [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] and completely click-through, and it
 * only ever *adds* light — vignette, tint, grain, scanlines. It cannot re-shade the pixels of the app
 * below (that would need screen capture, which this app deliberately does not do), and the
 * notification says exactly what is on screen.
 */
class ScreenFxService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var fxView: FxView? = null
    private var params: WindowManager.LayoutParams? = null
    private var observeJob: Job? = null

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

            ACTION_TOGGLE -> if (fxView != null) {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            } else {
                show()
            }

            ACTION_CYCLE -> cyclePreset()

            ACTION_REFRESH -> fxView?.invalidate()

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
        if (fxView != null) return
        if (!container.windows.canDrawOverlays()) {
            container.toastFeedback.show(getString(R.string.msg_needs_overlay), CommandFeedback.Kind.WARNING)
            stopSelf()
            return
        }
        val view = FxView(this, container.settings)
        fxView = view
        val layoutParams = container.windows.layoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0,
        ).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        params = layoutParams
        // Deliberately no blur behind: an FX overlay must never re-render (or obscure) the game
        // underneath it — it only adds light.
        isRunning = true
        startForeground(Notifications.ID_SCREEN_FX, notification())
        try {
            container.windows.addView(view, layoutParams)
        } catch (t: Throwable) {
            teardown()
            stopSelf()
            return
        }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = scope.launch {
            container.settings.changes.collect {
                fxView?.invalidate()
                container.notifications.notify(Notifications.ID_SCREEN_FX, notification())
            }
        }
    }

    private fun cyclePreset() {
        val settings = container.settings
        val index = FxPresets.ALL.indexOfFirst { it.id == settings.fxPreset }
        val next = FxPresets.ALL[(index + 1).coerceAtLeast(0) % FxPresets.ALL.size]
        settings.fxPreset = next.id
        fxView?.invalidate()
        container.notifications.notify(Notifications.ID_SCREEN_FX, notification())
        container.toastFeedback.show(
            getString(R.string.fx_preset_changed, getString(next.nameRes)),
            CommandFeedback.Kind.INFO,
        )
    }

    private fun notification() = container.notifications.build(
        channelId = Notifications.CHANNEL_OVERLAY,
        title = getString(R.string.notif_fx_title),
        text = getString(
            R.string.notif_fx_text,
            getString(FxPresets.byId(container.settings.fxPreset).nameRes),
        ),
        contentIntent = container.notifications.pendingActivity(
            Intent(this, com.dynablox.launcher.ui.LauncherActivity::class.java)
                .putExtra(com.dynablox.launcher.ui.LauncherActivity.EXTRA_OPEN_FX, true), 51,
        ),
        actions = listOf(
            getString(R.string.notif_action_next) to
                container.notifications.pendingService(ScreenFxService::class.java, ACTION_CYCLE, 52),
            getString(R.string.notif_action_hide) to
                container.notifications.pendingService(ScreenFxService::class.java, ACTION_HIDE, 53),
        ),
    )

    private fun teardown() {
        observeJob?.cancel()
        observeJob = null
        fxView?.let { container.windows.removeView(it) }
        fxView = null
        params = null
        isRunning = false
    }

    companion object {
        const val ACTION_SHOW = "com.dynablox.launcher.fx.SHOW"
        const val ACTION_HIDE = "com.dynablox.launcher.fx.HIDE"
        const val ACTION_STOP = "com.dynablox.launcher.fx.STOP"
        const val ACTION_TOGGLE = "com.dynablox.launcher.fx.TOGGLE"
        const val ACTION_CYCLE = "com.dynablox.launcher.fx.CYCLE"
        const val ACTION_REFRESH = "com.dynablox.launcher.fx.REFRESH"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

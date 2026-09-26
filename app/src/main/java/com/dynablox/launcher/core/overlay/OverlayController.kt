package com.dynablox.launcher.core.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dynablox.launcher.service.ControlsService
import com.dynablox.launcher.service.MenuHubService
import com.dynablox.launcher.service.PerfHudService
import com.dynablox.launcher.service.ScreenFxService

/**
 * Starts/stops the four overlay services and reports their live state.
 *
 * State is mirrored in each service's companion flag — the cheapest reliable source of truth for
 * "is this floating right now?" without binding to the services.
 */
class OverlayController(private val context: Context, private val windows: OverlayWindows) {

    val isMenuHubRunning: Boolean get() = MenuHubService.isRunning
    val isHudRunning: Boolean get() = PerfHudService.isRunning
    val isControlsRunning: Boolean get() = ControlsService.isRunning
    val isFxRunning: Boolean get() = ScreenFxService.isRunning

    val anyRunning: Boolean
        get() = isMenuHubRunning || isHudRunning || isControlsRunning || isFxRunning

    fun startMenuHub(): Boolean = start(MenuHubService::class.java, MenuHubService.ACTION_SHOW)
    fun stopMenuHub() = stop(MenuHubService::class.java)
    fun toggleMenuHub(): Boolean =
        if (isMenuHubRunning) {
            stopMenuHub(); false
        } else {
            startMenuHub()
        }

    fun toggleMenuHubPanel() {
        if (!isMenuHubRunning) startMenuHub() else command(MenuHubService::class.java, MenuHubService.ACTION_TOGGLE_PANEL)
    }

    fun startHud(): Boolean = start(PerfHudService::class.java, PerfHudService.ACTION_SHOW)
    fun stopHud() = stop(PerfHudService::class.java)
    fun toggleHud(): Boolean = if (isHudRunning) {
        stopHud(); false
    } else {
        startHud()
    }

    fun cycleHudMode() = command(PerfHudService::class.java, PerfHudService.ACTION_CYCLE_MODE)

    fun startControls(): Boolean = start(ControlsService::class.java, ControlsService.ACTION_SHOW)
    fun stopControls() = stop(ControlsService::class.java)
    fun toggleControls(): Boolean = if (isControlsRunning) {
        stopControls(); false
    } else {
        startControls()
    }

    fun startFx(): Boolean = start(ScreenFxService::class.java, ScreenFxService.ACTION_SHOW)
    fun stopFx() = stop(ScreenFxService::class.java)
    fun toggleFx(): Boolean = if (isFxRunning) {
        stopFx(); false
    } else {
        startFx()
    }

    fun stopEverything() {
        stopMenuHub()
        stopHud()
        stopControls()
        stopFx()
    }

    private fun start(service: Class<*>, action: String): Boolean {
        if (!windows.canDrawOverlays()) return false
        val intent = Intent(context, service).setAction(action)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun command(service: Class<*>, action: String) {
        if (!windows.canDrawOverlays()) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, service).setAction(action))
        } catch (_: Throwable) {
            // Ignore: the service is not startable right now.
        }
    }

    private fun stop(service: Class<*>) {
        try {
            context.stopService(Intent(context, service))
        } catch (_: Throwable) {
            // Ignore.
        }
    }
}

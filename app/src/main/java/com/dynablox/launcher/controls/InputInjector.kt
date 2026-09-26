package com.dynablox.launcher.controls

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.dynablox.launcher.accessibility.DynabloxAccessibilityService
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.shizuku.ShizukuController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InjectionResult(
    val success: Boolean,
    val method: InputInjector.Method,
    val latencyMs: Long,
    val message: String? = null,
) {
    companion object {
        fun unavailable(message: String) =
            InjectionResult(false, InputInjector.Method.NONE, 0L, message)
    }
}

/**
 * Turns virtual pad presses into real touch events inside the game.
 *
 * Two backends, both genuine:
 *  - **Shizuku**: `input tap|swipe|keyevent` executed with shell identity (fast, no service to
 *    enable, and the only option that keeps working while the accessibility service is paused).
 *  - **Accessibility**: `dispatchGesture` from our own service (no shell, no root, works after a
 *    single Settings toggle).
 *
 * Every injection is timed; the rolling latency is exposed in the UI so the user can see exactly
 * what a backend costs.
 */
class InputInjector(
    private val settings: AppSettings,
    private val shizuku: ShizukuController,
) {

    enum class Method { NONE, SHIZUKU, ACCESSIBILITY }

    private val _latency = MutableStateFlow(0f)
    val latency: StateFlow<Float> = _latency

    private val _lastMethod = MutableStateFlow(Method.NONE)
    val lastMethod: StateFlow<Method> = _lastMethod

    private var samples = 0
    private var latencySum = 0f

    fun activeMethod(): Method = when (settings.controlsBackend) {
        AppSettings.InjectionBackend.SHIZUKU ->
            if (shizuku.isGranted) Method.SHIZUKU else Method.NONE

        AppSettings.InjectionBackend.ACCESSIBILITY ->
            if (DynabloxAccessibilityService.isEnabled) Method.ACCESSIBILITY else Method.NONE

        AppSettings.InjectionBackend.AUTO -> when {
            shizuku.isGranted -> Method.SHIZUKU
            DynabloxAccessibilityService.isEnabled -> Method.ACCESSIBILITY
            else -> Method.NONE
        }
    }

    suspend fun gesture(
        gesture: PadGesture,
        targetX: Float,
        targetY: Float,
        screenWidth: Int,
        screenHeight: Int,
    ): InjectionResult {
        val x = (targetX * screenWidth).coerceIn(1f, (screenWidth - 1).toFloat())
        val y = (targetY * screenHeight).coerceIn(1f, (screenHeight - 1).toFloat())
        return when (gesture) {
            PadGesture.TAP -> tap(x, y, 30L)
            PadGesture.HOLD_SHORT -> tap(x, y, 220L)
            PadGesture.HOLD_LONG -> tap(x, y, 700L)
            PadGesture.SWIPE_UP -> swipe(x, y, x, y - screenHeight * 0.18f, 180L)
            PadGesture.SWIPE_DOWN -> swipe(x, y, x, y + screenHeight * 0.18f, 180L)
            PadGesture.SWIPE_LEFT -> swipe(x, y, x - screenWidth * 0.18f, y, 180L)
            PadGesture.SWIPE_RIGHT -> swipe(x, y, x + screenWidth * 0.18f, y, 180L)
            PadGesture.KEY_BACK -> key(KeyEvent.KEYCODE_BACK)
            PadGesture.KEY_HOME -> key(KeyEvent.KEYCODE_HOME)
            PadGesture.KEY_RECENTS -> key(KeyEvent.KEYCODE_APP_SWITCH)
        }
    }

    suspend fun tap(x: Float, y: Float, durationMs: Long = 30L): InjectionResult =
        run("tap ${x.toInt()} ${y.toInt()} ${durationMs.toInt()}") { method ->
            when (method) {
                Method.SHIZUKU -> {
                    val command = if (durationMs > 60L) {
                        "input swipe ${x.toInt()} ${y.toInt()} ${x.toInt()} ${y.toInt()} ${durationMs.toInt()}"
                    } else {
                        "input tap ${x.toInt()} ${y.toInt()}"
                    }
                    shizuku.exec(command).succeeded
                }

                Method.ACCESSIBILITY -> DynabloxAccessibilityService.current
                    ?.tap(x, y, durationMs) ?: false

                Method.NONE -> false
            }
        }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): InjectionResult =
        run("swipe") { method ->
            when (method) {
                Method.SHIZUKU -> shizuku.exec(
                    "input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} ${durationMs.toInt()}",
                ).succeeded

                Method.ACCESSIBILITY -> DynabloxAccessibilityService.current
                    ?.swipe(x1, y1, x2, y2, durationMs) ?: false

                Method.NONE -> false
            }
        }

    suspend fun key(keyCode: Int): InjectionResult = run("keyevent $keyCode") { method ->
        when (method) {
            Method.SHIZUKU -> shizuku.exec("input keyevent $keyCode").succeeded
            Method.ACCESSIBILITY -> when (keyCode) {
                KeyEvent.KEYCODE_BACK -> DynabloxAccessibilityService.current
                    ?.globalAction(AccessibilityGlobalAction.BACK) ?: false

                KeyEvent.KEYCODE_HOME -> DynabloxAccessibilityService.current
                    ?.globalAction(AccessibilityGlobalAction.HOME) ?: false

                KeyEvent.KEYCODE_APP_SWITCH -> DynabloxAccessibilityService.current
                    ?.globalAction(AccessibilityGlobalAction.RECENTS) ?: false

                else -> false
            }

            Method.NONE -> false
        }
    }

    private suspend fun run(label: String, block: suspend (Method) -> Boolean): InjectionResult {
        val method = activeMethod()
        if (method == Method.NONE) {
            return InjectionResult.unavailable("no injection backend ($label)")
        }
        val started = SystemClock.elapsedRealtime()
        val ok = try {
            block(method)
        } catch (t: Throwable) {
            Log.w(TAG, "injection failed: $label", t)
            false
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        if (ok) recordLatency(elapsed.toFloat())
        _lastMethod.value = method
        return InjectionResult(ok, method, elapsed, if (ok) null else "backend rejected $label")
    }

    private fun recordLatency(value: Float) {
        samples++
        latencySum += value
        _latency.value = if (samples > WINDOW) {
            // Exponential moving average once the warm-up window is full.
            (_latency.value * 0.7f) + (value * 0.3f)
        } else {
            latencySum / samples
        }
    }

    fun resetLatency() {
        samples = 0
        latencySum = 0f
        _latency.value = 0f
    }

    companion object {
        private const val TAG = "InputInjector"
        private const val WINDOW = 6
    }
}

/** Mirrors the AccessibilityService global action constants to keep call sites readable. */
object AccessibilityGlobalAction {
    val BACK: Int = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
    val HOME: Int = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
    val RECENTS: Int = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
    val NOTIFICATIONS: Int =
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    val QUICK_SETTINGS: Int =
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
    val POWER_DIALOG: Int =
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
    val TOGGLE_SPLIT_SCREEN: Int =
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
}

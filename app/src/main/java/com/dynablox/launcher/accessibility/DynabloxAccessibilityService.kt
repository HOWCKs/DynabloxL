package com.dynablox.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Gesture injector.
 *
 * This is the *no-privilege* backend for the virtual gamepad: `dispatchGesture` lets an
 * accessibility service synthesise real touch events inside any app, which is exactly what an
 * on-screen gamepad needs. Shizuku is still preferred when available because `input` is faster and
 * does not require the user to enable an accessibility service.
 *
 * It also exposes the Android global actions (home, recents, lock screen, quick settings…) so the
 * MenuHub can offer them as one-tap commands.
 */
class DynabloxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun tap(x: Float, y: Float, durationMs: Long = 40L): Boolean = gesture { path ->
        path.moveTo(x, y)
        path.lineTo(x + 0.5f, y + 0.5f)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 220L): Boolean =
        gesture(durationMs = durationMs) { path ->
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
        }

    private fun gesture(
        startMs: Long = 0L,
        durationMs: Long = 40L,
        build: (Path) -> Unit,
    ): Boolean {
        return try {
            val path = Path().also(build)
            val stroke = GestureDescription.StrokeDescription(
                path, startMs, durationMs.coerceIn(1L, MAX_GESTURE_MS),
            )
            val description = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(description, null, null)
        } catch (t: Throwable) {
            Log.w(TAG, "gesture failed", t)
            false
        }
    }

    fun globalAction(action: Int): Boolean = try {
        performGlobalAction(action)
    } catch (t: Throwable) {
        Log.w(TAG, "global action $action failed", t)
        false
    }

    fun lockScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            globalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }

    companion object {
        private const val TAG = "DbxAccessibility"
        private const val MAX_GESTURE_MS = 59_000L

        @Volatile
        private var instance: DynabloxAccessibilityService? = null

        val isEnabled: Boolean get() = instance != null

        val current: DynabloxAccessibilityService? get() = instance
    }
}

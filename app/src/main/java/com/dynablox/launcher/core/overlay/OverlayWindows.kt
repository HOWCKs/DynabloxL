package com.dynablox.launcher.core.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

/** Screen geometry helpers shared by every overlay window. */
data class ScreenMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val statusBarPx: Int,
    val navigationBarPx: Int,
) {
    val usableHeight: Int get() = (heightPx - statusBarPx - navigationBarPx).coerceAtLeast(heightPx / 2)
}

/**
 * Window plumbing for the floating surfaces (MenuHub, HUD, gamepad, FX).
 *
 * Every overlay uses `FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH`: touches inside the window
 * are ours, touches anywhere else fall through to the app underneath *and* still reach us as
 * ACTION_OUTSIDE, which is what makes "tap outside to collapse" work without swallowing the game.
 */
class OverlayWindows(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun metrics(): ScreenMetrics {
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            size.set(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(size)
        }
        val resources = context.resources
        val statusBar = resourceHeight("status_bar_height")
        val navBar = resourceHeight("navigation_bar_height")
        return ScreenMetrics(size.x, size.y, statusBar, navBar)
    }

    private fun resourceHeight(name: String): Int {
        val id = resources().getIdentifier(name, "dimen", "android")
        return if (id != 0) resources().getDimensionPixelSize(id) else 0
    }

    private fun resources() = context.resources

    fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    /**
     * Builds layout params for a content-sized overlay.
     *
     * @param focusable true only while the panel needs the IME (search field) — a focusable overlay
     *                  steals the back button, which we then handle explicitly.
     */
    fun layoutParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        focusable: Boolean = false,
        dimBehind: Boolean = false,
    ): WindowManager.LayoutParams {
        var flags = (
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (dimBehind) flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

        return WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            flags,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            this.x = x
            this.y = y
            windowAnimations = 0
            if (dimBehind) dimAmount = 0.35f
            softInputMode = if (focusable) {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            }
        }
    }

    /** Real backdrop blur behind a window (Android 12+), when the device allows it. */
    fun applyBlurBehind(params: WindowManager.LayoutParams, radiusPx: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            if (!windowManager.isCrossWindowBlurEnabled) return false
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.setBlurBehindRadius(radiusPx)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isBlurAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        try {
            windowManager.isCrossWindowBlurEnabled
        } catch (_: Throwable) {
            false
        }

    fun addView(view: android.view.View, params: WindowManager.LayoutParams) =
        windowManager.addView(view, params)

    fun updateView(view: android.view.View, params: WindowManager.LayoutParams) =
        windowManager.updateViewLayout(view, params)

    fun removeView(view: android.view.View) = try {
        if (view.isAttachedToWindow) windowManager.removeView(view)
    } catch (_: Throwable) {
        // Already detached.
    }
}

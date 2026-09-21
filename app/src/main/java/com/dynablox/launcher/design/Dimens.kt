package com.dynablox.launcher.design

import android.content.Context
import com.dynablox.launcher.core.AppSettings
import kotlin.math.roundToInt

/**
 * Global interface scale. Every custom view multiplies its dp values through here so the
 * "increase interface size" accessibility option works app-wide, including inside overlays
 * (which have no Activity to recreate).
 */
object Dimens {

    @Volatile
    var factor: Float = 1f
        private set

    fun apply(settings: AppSettings) {
        factor = settings.densityScale
    }

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density * factor

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density * factor).roundToInt()

    fun sp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.scaledDensity * factor

    /** Inverse of [dp] — used when persisting a dragged position in dp. */
    fun toDp(context: Context, px: Float): Float =
        px / (context.resources.displayMetrics.density * factor)
}

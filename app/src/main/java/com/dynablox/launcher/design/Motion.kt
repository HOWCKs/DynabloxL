package com.dynablox.launcher.design

import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import com.dynablox.launcher.core.AppSettings

/**
 * Motion language for the whole product.
 *
 * Three user-selectable characters (precise / spring / instant) plus a hard accessibility
 * override: when "reduce motion" is on, every animation collapses to <= 80 ms and idle breathing
 * loops are disabled entirely.
 */
object Motion {

    @Volatile
    var reduceMotion: Boolean = false
        private set

    @Volatile
    var style: AppSettings.AnimationStyle = AppSettings.AnimationStyle.SPRING
        private set

    /** Material "standard" curve: quick start, long gentle settle. */
    val standard: TimeInterpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    /** Underdamped overshoot used for the radial expansion. */
    val spring: TimeInterpolator = PathInterpolator(0.30f, 1.42f, 0.48f, 1.0f)

    /** Emphasised deceleration for panels arriving on screen. */
    val decelerate: TimeInterpolator = DecelerateInterpolator(1.9f)

    /** Tight, mechanical curve for button presses. */
    val precise: TimeInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    fun apply(settings: AppSettings) {
        reduceMotion = settings.reduceMotion
        style = settings.animationStyle
    }

    fun duration(baseMs: Long): Long = when {
        reduceMotion -> minOf(baseMs, 80L)
        style == AppSettings.AnimationStyle.INSTANT -> 0L
        style == AppSettings.AnimationStyle.PRECISE -> (baseMs * 0.72f).toLong()
        else -> baseMs
    }

    fun interpolator(): TimeInterpolator = when {
        reduceMotion -> precise
        style == AppSettings.AnimationStyle.PRECISE -> precise
        style == AppSettings.AnimationStyle.INSTANT -> precise
        else -> spring
    }

    fun expandInterpolator(): TimeInterpolator = if (reduceMotion) decelerate else spring

    /**
     * Micro-compression used by every tactile element: the body sinks by ~4% and the shadow
     * tightens, which is what makes a press feel physical.
     */
    fun press(view: View, pressed: Boolean, sink: Float = 0.955f) {
        val target = if (pressed) sink else 1f
        val anim = view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration(if (pressed) 110L else 190L))
            .setInterpolator(if (pressed) precise else spring)
        anim.start()
    }

    /** Ambient "breathing" is disabled whenever motion should be reduced. */
    fun ambientAllowed(): Boolean = !reduceMotion
}

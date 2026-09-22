package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.MaterialState
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.Tint

/**
 * Base class for every hand-drawn component in the design system.
 *
 * It resolves the active [ThemeTokens], owns a [MaterialState] shader cache, converts dp through
 * the global interface scale and exposes a [refreshTheme] hook that overlays call when the user
 * flips the theme at runtime (overlays cannot be recreated like Activities).
 */
abstract class SkeuoWidget : View {

    protected var tokens: ThemeTokens = ThemeTokens.from(context)
    protected val materialState = MaterialState()
    protected val box = RectF()
    protected var density: Float = resources.displayMetrics.density

    protected val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    protected val mediumPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    protected val labelPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.14f
    }

    constructor(context: Context) : super(context) {
        bootstrap(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        bootstrap(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        bootstrap(attrs)
    }

    private fun bootstrap(attrs: AttributeSet?) {
        density = resources.displayMetrics.density
        tokens = SkeuoTheme.tokens(context)
        readAttributes(attrs)
        onThemeApplied()
    }

    protected open fun readAttributes(attrs: AttributeSet?) = Unit

    protected open fun onThemeApplied() = Unit

    /** Re-resolves palette + scale. Called by overlays after a settings change. */
    open fun refreshTheme() {
        density = resources.displayMetrics.density
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        onThemeApplied()
        requestLayout()
        invalidate()
    }

    protected fun dp(value: Float): Float = Dimens.dp(context, value)

    protected fun dp(value: Int): Int = Dimens.dp(context, value)

    protected fun sp(value: Float): Float = Dimens.sp(context, value)

    protected fun tick() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    protected fun tickLight() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        box.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (w - paddingRight).toFloat(),
            (h - paddingBottom).toFloat(),
        )
        materialState.invalidate()
        onBoundsChanged(w, h)
    }

    protected open fun onBoundsChanged(width: Int, height: Int) = Unit

    /**
     * Draws the visible focus ring required for keyboard / switch-access navigation.
     * Subclasses call this last in `onDraw` so the ring sits above the material.
     */
    protected fun drawFocusRing(canvas: Canvas, box: RectF, radius: Float) {
        if (!isFocused) return
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = Tint.alphaFraction(tokens.accent, 0.95f)
        }
        val inset = dp(3f)
        canvas.drawRoundRect(
            box.left - inset, box.top - inset, box.right + inset, box.bottom + inset,
            radius + inset, radius + inset, paint,
        )
    }

    protected open fun defaultFocusRadius(): Float = dp(14f)
}

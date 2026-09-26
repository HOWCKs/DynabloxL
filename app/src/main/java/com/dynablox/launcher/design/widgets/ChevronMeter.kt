package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.Tint

/**
 * The stacked chevron meter that flanks the console (CPU on one side, RAM on the other).
 *
 * Why chevrons instead of a bar: a bar reads as a *quantity*, a stack of discrete arrows reads as
 * *load building up*, which is what a booster is communicating. It also degrades gracefully — at
 * small sizes a bar becomes a sliver, while a stack just drops segments.
 *
 * Honesty rule: segments light from a real 0..1 value. When the value is unknown the whole stack
 * stays unlit and the label is dimmed, instead of animating something invented.
 */
class ChevronMeter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), SkeuoComponent {

    /** Which way the arrows point — mirrored so both flanks aim at the centre of the console. */
    enum class Side { LEFT, RIGHT }

    var side: Side = Side.LEFT
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Caption under the stack, e.g. "CPU". Kept short: the flank is narrow by design. */
    var labelText: String? = null
        set(value) {
            field = value
            contentDescription = value
            invalidate()
        }

    /** 0..1, or null when the source is unavailable. */
    var value: Float? = null
        set(input) {
            val clamped = input?.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** Overrides the accent; used to tint the flanks with the engaged mode's colour. */
    var tintOverride: Int? = null
        set(input) {
            field = input
            invalidate()
        }

    var segments: Int = 7
        set(input) {
            val safe = input.coerceIn(3, 16)
            if (field != safe) {
                field = safe
                invalidate()
            }
        }

    private var tokens: ThemeTokens = SkeuoTheme.tokens(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val box = RectF()

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Announces the load as a percentage, so the meter is not a colour-only signal. */
    fun announce(): String {
        val v = value
        val name = labelText ?: ""
        return if (v == null) name else "$name ${(v * 100f).toInt()}%"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val label = labelText
        val labelHeight = if (label == null) 0f else Dimens.sp(context, 11f) * 1.9f
        val stackHeight = (h - labelHeight).coerceAtLeast(0f)
        if (stackHeight <= 0f) return

        val gap = Dimens.dp(context, 2.5f)
        val unit = ((stackHeight + gap) / segments - gap).coerceAtLeast(1f)
        val lit = value?.let { (it * segments).toInt().coerceIn(0, segments) } ?: 0
        val accent = tintOverride ?: tokens.accent

        // Bottom-up: load builds towards the top, matching the mental model of a level.
        for (i in 0 until segments) {
            val top = stackHeight - (i + 1) * unit - i * gap
            box.set(0f, top, w, top + unit)
            val on = i < lit
            // The top third is the danger band: it turns warning/danger even mid-scale, because a
            // CPU pinned at 90% matters more than the exact number.
            val color = when {
                !on -> tokens.ledOff
                i >= segments - 2 -> tokens.danger
                i >= segments - 4 -> tokens.warning
                else -> accent
            }
            paint.color = if (on) color else Tint.alphaFraction(color, 0.55f)
            chevron(path, box, side)
            canvas.drawPath(path, paint)
        }

        if (label != null) {
            textPaint.textSize = Dimens.sp(context, 11f)
            textPaint.letterSpacing = 0.10f
            textPaint.color = if (value == null) tokens.textFaint else tokens.textSecondary
            val baseline = h - (labelHeight - textPaint.textSize) / 2f - textPaint.descent()
            canvas.drawText(label, w / 2f, baseline, textPaint)
        }
    }

    /** One arrow segment: a rectangle with a notch cut into its trailing edge. */
    private fun chevron(target: Path, rect: RectF, side: Side) {
        val notch = rect.width() * 0.26f
        target.reset()
        if (side == Side.LEFT) {
            target.moveTo(rect.left, rect.top)
            target.lineTo(rect.right - notch, rect.top)
            target.lineTo(rect.right, rect.centerY())
            target.lineTo(rect.right - notch, rect.bottom)
            target.lineTo(rect.left, rect.bottom)
        } else {
            target.moveTo(rect.right, rect.top)
            target.lineTo(rect.left + notch, rect.top)
            target.lineTo(rect.left, rect.centerY())
            target.lineTo(rect.left + notch, rect.bottom)
            target.lineTo(rect.right, rect.bottom)
        }
        target.close()
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        invalidate()
    }
}

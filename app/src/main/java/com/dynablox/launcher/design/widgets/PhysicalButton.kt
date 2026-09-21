package com.dynablox.launcher.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Tint

/**
 * A machined keycap.
 *
 * Press response is physical: the cap sinks ~1.6 dp, the contact shadow tightens, the specular
 * band disappears and an extra inner shade makes the cap read as recessed into the deck. Release
 * springs back using the global motion character (precise / spring / instant) and collapses to a
 * single frame when "reduce motion" is enabled.
 */
class PhysicalButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    enum class Shape { ROUND, ROUNDED_SQUARE, CAPSULE }

    var label: String? = null
        set(value) {
            field = value
            if (value != null) contentDescription = value
            requestLayout()
            invalidate()
        }

    var subLabel: String? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var icon: Drawable? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** Null = follow the theme accent. */
    var accentOverride: Int? = null
        set(value) {
            field = value
            materialState.invalidate()
            invalidate()
        }

    val accentColor: Int
        get() = accentOverride ?: tokens.accent

    var ledColorOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val ledColor: Int
        get() = ledColorOverride ?: tokens.success

    var shape: Shape = Shape.CAPSULE
        set(value) {
            field = value
            materialState.invalidate()
            requestLayout()
            invalidate()
        }

    var material: Material = Material.CERAMIC
        set(value) {
            field = value
            materialState.invalidate()
            invalidate()
        }

    var ledOn: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var press: Float = 0f
    private var pressAnimator: ValueAnimator? = null
    private val iconBounds = Rect()
    private val bodyBox = RectF()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        isLongClickable = true

        val a = context.obtainStyledAttributes(attrs, R.styleable.PhysicalButton)
        label = a.getString(R.styleable.PhysicalButton_dbxLabel)
        subLabel = a.getString(R.styleable.PhysicalButton_dbxSubLabel)
        val iconRes = a.getResourceId(R.styleable.PhysicalButton_dbxIcon, 0)
        if (iconRes != 0) icon = ContextCompat.getDrawable(context, iconRes)
        if (a.hasValue(R.styleable.PhysicalButton_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.PhysicalButton_dbxAccentColor, tokens.accent)
        }
        shape = when (a.getInt(R.styleable.PhysicalButton_dbxButtonShape, 2)) {
            0 -> Shape.ROUND
            1 -> Shape.ROUNDED_SQUARE
            else -> Shape.CAPSULE
        }
        ledOn = a.getBoolean(R.styleable.PhysicalButton_dbxShowLed, false)
        if (a.hasValue(R.styleable.PhysicalButton_dbxLedColor)) {
            ledColorOverride = a.getColor(R.styleable.PhysicalButton_dbxLedColor, tokens.success)
        }
        val compact = a.getBoolean(R.styleable.PhysicalButton_dbxCompact, false)
        a.recycle()

        minimumHeight = dp(if (compact) 40f else 52f).toInt()
    }

    fun setIconResource(resId: Int) {
        icon = if (resId == 0) null else ContextCompat.getDrawable(context, resId)
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        animatePress(pressed)
        if (pressed) tick()
    }

    private fun animatePress(pressed: Boolean) {
        pressAnimator?.cancel()
        val target = if (pressed) 1f else 0f
        if (Motion.reduceMotion || Motion.duration(120L) == 0L) {
            press = target
            invalidate()
            return
        }
        pressAnimator = ValueAnimator.ofFloat(press, target).apply {
            duration = Motion.duration(if (pressed) 110L else 240L)
            interpolator = if (pressed) Motion.precise else Motion.spring
            addUpdateListener {
                press = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun radiusFor(w: Float, h: Float): Float = when (shape) {
        Shape.ROUND, Shape.CAPSULE -> minOf(w, h) / 2f
        Shape.ROUNDED_SQUARE -> minOf(w, h) * 0.28f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = when {
            shape == Shape.ROUND -> dp(64f)
            subLabel == null -> dp(54f)
            else -> dp(66f)
        }.toInt()
        val height = View.resolveSize(maxOf(desiredHeight, minimumHeight), heightMeasureSpec)

        mediumPaint.textSize = sp(14.5f)
        textPaint.textSize = sp(11.5f)
        val iconWidth = if (icon != null) dp(22f) + dp(10f) else 0f
        val labelWidth = label?.let { mediumPaint.measureText(it) } ?: 0f
        val subWidth = subLabel?.let { textPaint.measureText(it) } ?: 0f
        val desiredWidth = (iconWidth + maxOf(labelWidth, subWidth) + dp(40f)).toInt()
        val width = if (shape == Shape.ROUND) {
            height
        } else {
            View.resolveSize(maxOf(desiredWidth, minimumWidth), widthMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = radiusFor(w, h)
        val sink = dp(1.6f) * press

        bodyBox.set(dp(2f), dp(2f) + sink, w - dp(2f), h - dp(2f) - sink * 0.45f)

        val shadowStrength = (1f - press * 0.55f) * SkeuoTheme.shadowIntensity
        if (shadowStrength > 0.02f) {
            Skeuo.drawContactShadow(
                canvas, bodyBox, radius,
                Tint.alphaFraction(tokens.shadow, 0.9f),
                dp(4f) * shadowStrength,
                dp(3f) * shadowStrength,
            )
        }

        val highlighted = isActivated || isSelected
        val options = MaterialOptions(
            shadow = (0.6f + 0.4f * (1f - press)).coerceIn(0f, 1f),
            bevel = true,
            texture = true,
            specular = press < 0.85f,
            innerShade = true,
            tint = if (highlighted) accentColor else null,
            tintAmount = 0.24f,
        )
        Materials.paint(canvas, bodyBox, radius, material, tokens, materialState, options, density)

        if (press > 0.01f) {
            Skeuo.drawInnerShade(canvas, bodyBox, radius, tokens.shadow, dp(4f) * press, density)
        }

        drawContent(canvas, bodyBox)
        if (ledOn) drawLed(canvas, bodyBox)
        drawFocusRing(canvas, bodyBox, radius)
    }

    private fun drawContent(canvas: Canvas, area: RectF) {
        val iconDrawable = icon
        val highlighted = isActivated || isSelected
        val iconSize = dp(if (label == null) 26f else 22f)

        mediumPaint.textSize = sp(if (shape == Shape.CAPSULE) 14.5f else 13.5f)
        mediumPaint.color = tokens.textPrimary
        textPaint.textSize = sp(11.5f)
        textPaint.color = tokens.textSecondary

        if (shape == Shape.ROUND && label == null) {
            if (iconDrawable != null) {
                val left = area.centerX() - iconSize / 2f
                val top = area.centerY() - iconSize / 2f
                iconBounds.set(
                    left.toInt(), top.toInt(),
                    (left + iconSize).toInt(), (top + iconSize).toInt(),
                )
                iconDrawable.bounds = iconBounds
                iconDrawable.setTint(if (highlighted) accentColor else tokens.iconTint)
                iconDrawable.alpha = (255 * (1f - press * 0.18f)).toInt()
                iconDrawable.draw(canvas)
            }
            return
        }

        val labelWidth = label?.let { mediumPaint.measureText(it) } ?: 0f
        val gap = if (iconDrawable != null && labelWidth > 0f) dp(10f) else 0f
        val totalWidth = (if (iconDrawable != null) iconSize else 0f) + gap + labelWidth
        var cursor = area.centerX() - totalWidth / 2f

        if (iconDrawable != null) {
            val iconTop = if (subLabel == null) {
                area.centerY() - iconSize / 2f
            } else {
                area.centerY() - iconSize / 2f - dp(6f)
            }
            iconBounds.set(
                cursor.toInt(), iconTop.toInt(),
                (cursor + iconSize).toInt(), (iconTop + iconSize).toInt(),
            )
            iconDrawable.bounds = iconBounds
            iconDrawable.setTint(if (highlighted) accentColor else tokens.iconTint)
            iconDrawable.draw(canvas)
            cursor += iconSize + gap
        }

        val label = label
        if (label != null) {
            val baseline = if (subLabel == null) {
                area.centerY() - (mediumPaint.descent() + mediumPaint.ascent()) / 2f
            } else {
                area.centerY() - dp(2f)
            }
            canvas.drawText(label, cursor, baseline, mediumPaint)
        }
        val sub = subLabel
        if (sub != null) {
            canvas.drawText(sub, cursor, area.centerY() + dp(13f), textPaint)
        }
    }

    private fun drawLed(canvas: Canvas, area: RectF) {
        val r = dp(3.4f)
        val cx = area.right - dp(13f)
        val cy = area.top + dp(13f)
        val color = if (ledOn) ledColor else tokens.ledOff

        if (ledOn) Skeuo.drawGlow(canvas, cx, cy, r * 3.6f, color, 0.5f)

        dotPaint.reset()
        dotPaint.isAntiAlias = true
        dotPaint.color = color
        canvas.drawCircle(cx, cy, r, dotPaint)

        dotPaint.style = Paint.Style.STROKE
        dotPaint.strokeWidth = dp(0.9f)
        dotPaint.color = Tint.alphaFraction(tokens.shadow, 0.7f)
        canvas.drawCircle(cx, cy, r, dotPaint)

        if (ledOn) {
            dotPaint.style = Paint.Style.FILL
            dotPaint.color = Tint.alphaFraction(Color.WHITE, 0.5f)
            canvas.drawCircle(cx - r * 0.3f, cy - r * 0.32f, r * 0.34f, dotPaint)
        }
    }

    override fun onThemeApplied() {
        // Nothing cached beyond the material state, which the base class already invalidates.
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        requestLayout()
        invalidate()
    }
}

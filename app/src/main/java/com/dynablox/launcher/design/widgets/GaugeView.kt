package com.dynablox.launcher.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Tint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Analogue instrument gauge: recessed dial, engraved ticks, coloured zones, a tapered needle with
 * a machined hub and a glass reflection over the top half.
 *
 * Used for CPU load, thermal headroom and FPS-vs-refresh budget, where a needle communicates trend
 * faster than a number.
 */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    data class Zone(val from: Float, val to: Float, val color: Int)

    var labelText: String? = null
        set(value) {
            field = value
            contentDescription = buildA11y()
            requestLayout()
            invalidate()
        }

    var unit: String? = null
        set(value) {
            field = value
            contentDescription = buildA11y()
            invalidate()
        }

    var maxValue: Float = 100f
        set(value) {
            field = if (value <= 0f) 1f else value
            invalidate()
        }

    var value: Float = 0f
        set(newValue) {
            val clamped = newValue.coerceIn(0f, maxValue)
            if (abs(field - clamped) < 0.001f) return
            field = clamped
            contentDescription = buildA11y()
            animateNeedle(clamped)
        }

    var zones: List<Zone> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var accentOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.accent

    var showDigital: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var needle = 0f
    private var needleAnimator: ValueAnimator? = null
    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePath = Path()
    private val arcBox = RectF()
    private val well = RectF()
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.GaugeView)
        labelText = a.getString(R.styleable.GaugeView_dbxLabel)
        unit = a.getString(R.styleable.GaugeView_dbxUnit)
        maxValue = a.getFloat(R.styleable.GaugeView_dbxValueMax, 100f)
        value = a.getFloat(R.styleable.GaugeView_dbxValue, 0f)
        if (a.hasValue(R.styleable.GaugeView_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.GaugeView_dbxAccentColor, tokens.accent)
        }
        a.recycle()
        needle = value
        contentDescription = buildA11y()
    }

    private fun buildA11y(): String {
        val label = labelText ?: context.getString(R.string.app_name)
        val unitText = unit ?: ""
        return "$label ${value.toInt()}$unitText"
    }

    private fun animateNeedle(target: Float) {
        needleAnimator?.cancel()
        if (Motion.duration(200L) == 0L) {
            needle = target
            invalidate()
            return
        }
        needleAnimator = ValueAnimator.ofFloat(needle, target).apply {
            duration = Motion.duration(420L)
            interpolator = Motion.expandInterpolator()
            addUpdateListener {
                needle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        needleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(112f).toInt()
        val size = min(
            View.resolveSize(desired, widthMeasureSpec),
            View.resolveSize(desired, heightMeasureSpec),
        )
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - dp(2f)
        well.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // bezel + recessed dial
        dialPaint.reset()
        dialPaint.isAntiAlias = true
        dialPaint.style = Paint.Style.STROKE
        dialPaint.strokeWidth = dp(3f)
        dialPaint.shader = Materials.rimShader(cx, cy, radius, tokens)
        canvas.drawCircle(cx, cy, radius - dp(1.5f), dialPaint)

        val dial = RectF()
        Skeuo.inset(well, dp(3f), dial)
        Materials.paint(
            canvas, dial, dial.width() / 2f, Material.INSET, tokens, materialState,
            MaterialOptions(specular = false, bevel = false, shadow = 1f),
            density,
        )

        val sweepStart = 135f
        val sweepTotal = 270f
        val arcRadius = dial.width() / 2f - dp(9f)
        arcBox.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)

        // zones
        zones.forEach { zone ->
            val from = (zone.from / maxValue).coerceIn(0f, 1f)
            val to = (zone.to / maxValue).coerceIn(0f, 1f)
            dialPaint.reset()
            dialPaint.isAntiAlias = true
            dialPaint.style = Paint.Style.STROKE
            dialPaint.strokeWidth = dp(3.4f)
            dialPaint.color = Tint.alphaFraction(zone.color, 0.55f)
            canvas.drawArc(arcBox, sweepStart + from * sweepTotal, (to - from) * sweepTotal, false, dialPaint)
        }

        // ticks
        val ticks = 10
        for (i in 0..ticks) {
            val angle = Math.toRadians((sweepStart + sweepTotal * i / ticks).toDouble())
            val major = i % 5 == 0
            val outer = arcRadius + dp(3.5f)
            val inner = outer - (if (major) dp(6f) else dp(3.4f))
            tickPaint.strokeWidth = dp(if (major) 1.5f else 0.9f)
            tickPaint.color = Tint.alphaFraction(tokens.textFaint, if (major) 0.85f else 0.45f)
            canvas.drawLine(
                cx + inner * cos(angle).toFloat(), cy + inner * sin(angle).toFloat(),
                cx + outer * cos(angle).toFloat(), cy + outer * sin(angle).toFloat(),
                tickPaint,
            )
        }

        // needle
        val needleAngle = Math.toRadians((sweepStart + sweepTotal * (needle / maxValue).coerceIn(0f, 1f)).toDouble())
        val needleLength = arcRadius - dp(2f)
        val tipX = cx + needleLength * cos(needleAngle).toFloat()
        val tipY = cy + needleLength * sin(needleAngle).toFloat()
        val perpAngle = needleAngle + Math.PI / 2
        val baseHalf = dp(2.6f)
        needlePath.reset()
        needlePath.moveTo(tipX, tipY)
        needlePath.lineTo(
            cx + baseHalf * cos(perpAngle).toFloat(),
            cy + baseHalf * sin(perpAngle).toFloat(),
        )
        needlePath.lineTo(
            cx - needleLength * 0.16f * cos(needleAngle).toFloat(),
            cy - needleLength * 0.16f * sin(needleAngle).toFloat(),
        )
        needlePath.lineTo(
            cx - baseHalf * cos(perpAngle).toFloat(),
            cy - baseHalf * sin(perpAngle).toFloat(),
        )
        needlePath.close()

        val needleColor = zones.lastOrNull { needle >= it.from && needle <= it.to }?.color ?: accentColor
        Skeuo.drawGlow(canvas, tipX, tipY, dp(10f), needleColor, 0.45f)
        needlePaint.reset()
        needlePaint.isAntiAlias = true
        needlePaint.color = needleColor
        canvas.drawPath(needlePath, needlePaint)
        needlePaint.color = Tint.alphaFraction(Color.WHITE, 0.35f)
        needlePaint.style = Paint.Style.STROKE
        needlePaint.strokeWidth = dp(0.6f)
        canvas.drawPath(needlePath, needlePaint)

        // hub cap
        needlePaint.style = Paint.Style.FILL
        needlePaint.color = tokens.metalLight
        canvas.drawCircle(cx, cy, dp(6.5f), needlePaint)
        needlePaint.color = Tint.darken(tokens.metalMid, 0.25f)
        canvas.drawCircle(cx, cy, dp(4.4f), needlePaint)
        needlePaint.color = Tint.alphaFraction(Color.WHITE, 0.4f)
        canvas.drawCircle(cx - dp(1.4f), cy - dp(1.6f), dp(1.5f), needlePaint)

        // digital read-out + engraved label
        if (showDigital) {
            valuePaint.textSize = dp(13f)
            valuePaint.color = tokens.textPrimary
            val text = value.toInt().toString() + (unit ?: "")
            canvas.drawText(text, cx, cy + arcRadius * 0.62f, valuePaint)
        }
        val label = labelText
        if (label != null) {
            labelPaint.textSize = sp(8.5f)
            labelPaint.color = tokens.textFaint
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(label.uppercase(), cx, cy - arcRadius * 0.48f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
        }

        // glass reflection over the top half
        dialPaint.reset()
        dialPaint.isAntiAlias = true
        dialPaint.shader = android.graphics.LinearGradient(
            cx, dial.top, cx, cy,
            Tint.alphaFraction(Color.WHITE, 0.09f), Tint.alpha(Color.WHITE, 0),
            android.graphics.Shader.TileMode.CLAMP,
        )
        val save = canvas.save()
        canvas.clipPath(Skeuo.roundRectPath(Path(), dial, dial.width() / 2f))
        canvas.drawRect(dial.left, dial.top, dial.right, cy, dialPaint)
        canvas.restoreToCount(save)

        drawFocusRing(canvas, well, radius)
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }
}

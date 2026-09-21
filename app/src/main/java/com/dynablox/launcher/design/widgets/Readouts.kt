package com.dynablox.launcher.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

/**
 * Small round status lamp: recessed lens, chrome collar, glow when lit.
 */
class LedIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var lit: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateA11yLabel()
                invalidate()
            }
        }

    var colorOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val ledColor: Int get() = colorOverride ?: tokens.success

    var labelText: String? = null
        set(value) {
            field = value
            updateA11yLabel()
            requestLayout()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val well = RectF()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LedIndicator)
        labelText = a.getString(R.styleable.LedIndicator_dbxLabel)
        if (a.hasValue(R.styleable.LedIndicator_dbxLedColor)) {
            colorOverride = a.getColor(R.styleable.LedIndicator_dbxLedColor, tokens.success)
        }
        lit = a.getBoolean(R.styleable.LedIndicator_dbxLit, false)
        a.recycle()
        updateA11yLabel()
    }

    private fun updateA11yLabel() {
        val text = labelText
        contentDescription = if (text.isNullOrBlank()) {
            null
        } else {
            "$text: ${if (lit) "on" else "off"}"
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dot = dp(14f).toInt()
        val hasLabel = !labelText.isNullOrBlank()
        labelPaint.textSize = sp(9.5f)
        val textWidth = labelText?.let { labelPaint.measureText(it) } ?: 0f
        val desiredWidth = if (hasLabel) (dot + dp(6f) + textWidth).toInt() else dot
        setMeasuredDimension(
            View.resolveSize(desiredWidth, widthMeasureSpec),
            View.resolveSize(dot, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val dot = dp(14f)
        val cy = height / 2f
        val cx = dot / 2f + dp(1f)
        well.set(cx - dot / 2f, cy - dot / 2f, cx + dot / 2f, cy + dot / 2f)
        val radius = dot / 2f

        // recessed collar
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Tint.darken(tokens.surfaceInset, 0.2f)
        canvas.drawCircle(cx, cy, radius, paint)
        Skeuo.drawInnerShade(canvas, well, radius, Color.BLACK, dp(1.6f), density)

        val lens = RectF()
        Skeuo.inset(well, dp(1.8f), lens)
        val lensRadius = lens.width() / 2f

        if (lit) {
            Skeuo.drawGlow(canvas, cx, cy, radius * 2.6f, ledColor, 0.6f)
            paint.shader = android.graphics.RadialGradient(
                cx - lensRadius * 0.3f, cy - lensRadius * 0.35f, lensRadius * 1.5f,
                intArrayOf(Tint.lighten(ledColor, 0.45f), ledColor, Tint.darken(ledColor, 0.4f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.shader = android.graphics.RadialGradient(
                cx - lensRadius * 0.3f, cy - lensRadius * 0.35f, lensRadius * 1.5f,
                intArrayOf(Tint.lighten(tokens.ledOff, 0.18f), tokens.ledOff, Tint.darken(tokens.ledOff, 0.4f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, lensRadius, paint)
        paint.shader = null

        // specular dot
        paint.color = Tint.alphaFraction(Color.WHITE, if (lit) 0.62f else 0.22f)
        canvas.drawCircle(cx - lensRadius * 0.28f, cy - lensRadius * 0.32f, lensRadius * 0.3f, paint)

        val text = labelText
        if (!text.isNullOrBlank()) {
            labelPaint.textSize = sp(9.5f)
            labelPaint.color = if (lit) tokens.textSecondary else tokens.textFaint
            val baseline = cy - (labelPaint.descent() + labelPaint.ascent) / 2f
            canvas.drawText(text, dot + dp(7f), baseline, labelPaint)
        }
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        invalidate()
    }
}

/**
 * Seven-segment flavoured LCD readout: recessed smoked glass, ghost "off" segments behind the
 * live digits and a soft accent bloom. Used for FPS, frame time and latency where numeric
 * legibility at a glance matters more than decoration.
 */
class DigitalReadout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var labelText: String? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var unit: String? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var value: String = "--"
        set(newValue) {
            if (field != newValue) {
                field = newValue
                flash()
                invalidate()
            }
        }

    var digits: Int = 3
        set(newValue) {
            field = newValue.coerceIn(1, 8)
            invalidate()
        }

    var accentOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.accent

    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }
    private val well = RectF()
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPath = Path()
    private var glossShader: Shader? = null
    private var glossHeight = -1
    private var flashAmount = 0f
    private var flashAnimator: ValueAnimator? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.DigitalReadout)
        labelText = a.getString(R.styleable.DigitalReadout_dbxLabel)
        unit = a.getString(R.styleable.DigitalReadout_dbxUnit)
        value = a.getString(R.styleable.DigitalReadout_dbxValue) ?: "--"
        digits = a.getInt(R.styleable.DigitalReadout_dbxDigits, 3)
        if (a.hasValue(R.styleable.DigitalReadout_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.DigitalReadout_dbxAccentColor, tokens.accent)
        }
        a.recycle()
    }

    private fun flash() {
        if (!Motion.ambientAllowed()) return
        flashAnimator?.cancel()
        flashAnimator = ValueAnimator.ofFloat(0.55f, 0f).apply {
            duration = Motion.duration(360L)
            interpolator = Motion.decelerate
            addUpdateListener {
                flashAmount = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        flashAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        digitPaint.textSize = sp(24f)
        val ghost = "8".repeat(digits)
        val valueWidth = maxOf(digitPaint.measureText(ghost), digitPaint.measureText(value))
        unitPaint.textSize = sp(9.5f)
        val unitWidth = unit?.let { unitPaint.measureText(it) + dp(4f) } ?: 0f
        val desiredWidth = (valueWidth + unitWidth + dp(24f)).toInt()
        val desiredHeight = dp(if (labelText == null) 40f else 52f).toInt()
        setMeasuredDimension(
            View.resolveSize(maxOf(desiredWidth, minimumWidth), widthMeasureSpec),
            View.resolveSize(maxOf(desiredHeight, minimumHeight), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        well.set(box)
        val radius = dp(10f)

        Materials.paint(
            canvas, well, radius, Material.INSET, tokens, materialState,
            MaterialOptions(specular = false, bevel = false, innerShade = true, shadow = 1f),
            density,
        )
        Skeuo.drawHairline(canvas, well, radius, Tint.alphaFraction(tokens.chromeHi, 0.10f), 1f, density)

        val label = labelText
        var contentTop = well.top
        if (label != null) {
            labelPaint.textSize = sp(8.5f)
            labelPaint.color = tokens.textFaint
            canvas.drawText(label, well.left + dp(9f), well.top + dp(12f), labelPaint)
            contentTop = well.top + dp(12f)
        }

        val available = well.height() - (contentTop - well.top)
        digitPaint.textSize = (available * 0.78f).coerceAtMost(sp(26f))
        ghostPaint.textSize = digitPaint.textSize

        val ghost = "8".repeat(digits)
        val centerX = well.centerX() - (unit?.let { unitPaint.textSize = sp(9.5f); unitPaint.measureText(it) } ?: 0f) / 2f
        val baseline = contentTop + available / 2f - (digitPaint.descent() + digitPaint.ascent) / 2f + dp(1f)

        ghostPaint.color = Tint.alphaFraction(accentColor, 0.10f)
        canvas.drawText(ghost, centerX, baseline, ghostPaint)

        if (flashAmount > 0.01f) {
            Skeuo.drawGlow(canvas, centerX, baseline + digitPaint.ascent / 2f, well.width() * 0.45f, accentColor, flashAmount)
        }

        digitPaint.color = accentColor
        canvas.drawText(value, centerX, baseline, digitPaint)

        val unitText = unit
        if (unitText != null) {
            unitPaint.textSize = sp(9.5f)
            unitPaint.color = Tint.alphaFraction(tokens.textSecondary, 0.85f)
            val valueWidth = digitPaint.measureText(value)
            canvas.drawText(
                unitText,
                centerX + valueWidth / 2f + dp(4f),
                baseline,
                unitPaint,
            )
        }

        // glass reflection over the whole well
        val glossHeightNow = (well.height() * 0.5f).toInt()
        if (glossShader == null || glossHeightNow != glossHeight) {
            glossHeight = glossHeightNow
            glossShader = LinearGradient(
                well.left, well.top, well.left, well.top + well.height() * 0.5f,
                Tint.alphaFraction(Color.WHITE, 0.06f), Tint.alpha(Color.WHITE, 0),
                Shader.TileMode.CLAMP,
            )
        }
        glossPaint.shader = glossShader
        val save = canvas.save()
        canvas.clipPath(Skeuo.roundRectPath(glossPath, well, radius))
        canvas.drawRect(well, glossPaint)
        canvas.restoreToCount(save)

        drawFocusRing(canvas, well, radius)
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }
}

/**
 * Rolling history graph used by the HUD and the launcher telemetry deck.
 * Values are pushed as samples; the view keeps a fixed-size ring buffer and draws a filled area
 * with an engraved grid, so it stays readable at 40 dp tall.
 */
class Sparkline @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var capacity: Int = 60
        set(value) {
            field = value.coerceIn(8, 600)
            samples = FloatArray(field) { 0f }
            head = 0
            count = 0
            invalidate()
        }

    var maxValue: Float = 100f
        set(value) {
            field = if (value <= 0f) 1f else value
            invalidate()
        }

    var threshold: Float? = null
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

    private var samples = FloatArray(capacity) { 0f }
    private var head = 0
    private var count = 0
    private val linePath = Path()
    private val areaPath = Path()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val well = RectF()

    fun push(value: Float) {
        samples[head] = value
        head = (head + 1) % capacity
        if (count < capacity) count++
        invalidate()
    }

    fun reset() {
        samples = FloatArray(capacity) { 0f }
        head = 0
        count = 0
        invalidate()
    }

    fun average(): Float {
        if (count == 0) return 0f
        var sum = 0f
        for (i in 0 until count) sum += samples[i]
        return sum / count
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.resolveSize(dp(120f).toInt(), widthMeasureSpec),
            View.resolveSize(dp(38f).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        well.set(box)
        val radius = dp(8f)
        Materials.paint(
            canvas, well, radius, Material.INSET, tokens, materialState,
            MaterialOptions(specular = false, bevel = false, shadow = 1f),
            density,
        )

        val pad = dp(5f)
        val left = well.left + pad
        val right = well.right - pad
        val top = well.top + pad
        val bottom = well.bottom - pad
        if (right <= left || bottom <= top) return

        // engraved grid
        gridPaint.strokeWidth = dp(0.7f)
        gridPaint.color = Tint.alphaFraction(tokens.chromeHi, 0.06f)
        for (i in 1 until 3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        for (i in 1 until 6) {
            val x = left + (right - left) * i / 6f
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }

        val n = count
        if (n < 2) return
        val step = (right - left) / (capacity - 1).toFloat()

        linePath.reset()
        areaPath.reset()
        var first = true
        var lastX = left
        var lastY = bottom
        for (i in 0 until n) {
            val index = if (n == capacity) {
                (head + i) % capacity
            } else {
                i
            }
            val offset = if (n == capacity) i else (capacity - n + i)
            val v = samples[index].coerceIn(0f, maxValue)
            val x = left + offset * step
            val y = bottom - (v / maxValue) * (bottom - top)
            if (first) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, bottom)
                areaPath.lineTo(x, y)
                first = false
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        areaPath.lineTo(lastX, bottom)
        areaPath.close()

        areaPaint.shader = LinearGradient(
            left, top, left, bottom,
            Tint.alphaFraction(accentColor, 0.42f),
            Tint.alphaFraction(accentColor, 0.02f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(areaPath, areaPaint)

        linePaint.strokeWidth = dp(1.5f)
        linePaint.color = accentColor
        canvas.drawPath(linePath, linePaint)

        threshold?.let { limit ->
            val y = bottom - (limit.coerceIn(0f, maxValue) / maxValue) * (bottom - top)
            gridPaint.strokeWidth = dp(1f)
            gridPaint.color = Tint.alphaFraction(tokens.warning, 0.6f)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        // live head dot
        Skeuo.drawGlow(canvas, lastX, lastY, dp(7f), accentColor, 0.7f)
        linePaint.style = Paint.Style.FILL
        linePaint.color = Tint.lighten(accentColor, 0.4f)
        canvas.drawCircle(lastX, lastY, dp(2.1f), linePaint)
        linePaint.style = Paint.Style.STROKE
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }
}

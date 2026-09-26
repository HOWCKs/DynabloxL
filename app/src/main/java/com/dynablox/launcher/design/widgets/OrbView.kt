package com.dynablox.launcher.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoPathHolder
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Textures
import com.dynablox.launcher.design.Tint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The MenuHub core.
 *
 * A physical command knob floating above the desktop: machined bezel, smoked-glass body, ambient
 * status ring, specular highlight and a contact shadow. It exposes every state the brief calls
 * for — resting, hover, pressed, expanded, notifying — through [setState].
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    enum class Shape { CIRCLE, ROUNDED_SQUARE, CAPSULE }

    enum class Status { IDLE, ACTIVE, BUSY, WARNING, ERROR }

    var shape: Shape = Shape.CIRCLE
        set(value) {
            field = value
            invalidate()
        }

    /** 0..1 — how much of the status ring is lit (maps to system load or FPS headroom). */
    var activity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var status: Status = Status.IDLE
        set(value) {
            field = value
            invalidate()
        }

    var badge: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var expanded: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateExpand(value)
            }
        }

    var iconRes: Int = R.drawable.ic_hub
        set(value) {
            field = value
            iconDrawable = ContextCompat.getDrawable(context, value)
            invalidate()
        }

    var ringEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var tooltip: String? = null
        set(value) {
            field = value
            tooltipText = value
        }

    private var iconDrawable: Drawable? = ContextCompat.getDrawable(context, iconRes)
    private var pressAmount = 0f
    private var expandAmount = 0f
    private var hoverAmount = 0f
    private var breath = 0f
    private var breathAnimator: ValueAnimator? = null
    private var pressAnimator: ValueAnimator? = null
    private var expandAnimator: ValueAnimator? = null
    private var hoverAnimator: ValueAnimator? = null

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconRect = Rect()
    private val bodyBox = RectF()
    private val innerBox = RectF()
    private var noiseShader: Shader? = null

    init {
        isClickable = true
        isFocusable = true
        isLongClickable = true
        contentDescription = context.getString(R.string.cd_orb)
        tooltipText = context.getString(R.string.menuhub_open)
    }

    private val statusColor: Int
        get() = when (status) {
            Status.IDLE -> tokens.accent
            Status.ACTIVE -> tokens.success
            Status.BUSY -> tokens.accent
            Status.WARNING -> tokens.warning
            Status.ERROR -> tokens.danger
        }

    // ------------------------------------------------------------------ states

    fun setPressedState(pressed: Boolean) {
        pressAnimator?.cancel()
        val target = if (pressed) 1f else 0f
        if (Motion.duration(120L) == 0L) {
            pressAmount = target
            invalidate()
            return
        }
        pressAnimator = ValueAnimator.ofFloat(pressAmount, target).apply {
            duration = Motion.duration(if (pressed) 100L else 260L)
            interpolator = if (pressed) Motion.precise else Motion.spring
            addUpdateListener {
                pressAmount = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        setPressedState(pressed)
        if (pressed) tick()
    }

    private fun animateExpand(toExpanded: Boolean) {
        expandAnimator?.cancel()
        val target = if (toExpanded) 1f else 0f
        if (Motion.duration(160L) == 0L) {
            expandAmount = target
            invalidate()
            return
        }
        expandAnimator = ValueAnimator.ofFloat(expandAmount, target).apply {
            duration = Motion.duration(if (toExpanded) 240L else 200L)
            interpolator = Motion.expandInterpolator()
            addUpdateListener {
                expandAmount = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> setHover(true)
            MotionEvent.ACTION_HOVER_EXIT -> setHover(false)
        }
        return super.onHoverEvent(event)
    }

    private fun setHover(hovering: Boolean) {
        val target = if (hovering) 1f else 0f
        if (hoverAmount == target) return
        hoverAnimator?.cancel()
        if (Motion.duration(120L) == 0L) {
            hoverAmount = target
            invalidate()
            return
        }
        hoverAnimator = ValueAnimator.ofFloat(hoverAmount, target).apply {
            duration = Motion.duration(if (hovering) 200L else 240L)
            interpolator = Motion.standard
            addUpdateListener {
                hoverAmount = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        setHover(gainFocus)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBreathing()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        pressAnimator?.cancel()
        expandAnimator?.cancel()
        hoverAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun startBreathing() {
        stopBreathing()
        if (!Motion.ambientAllowed()) return
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = Motion.standard
            addUpdateListener {
                breath = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathing() {
        breathAnimator?.cancel()
        breathAnimator = null
        breath = 0f
    }

    // ------------------------------------------------------------------ measure / draw

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(72f).toInt()
        val w = View.resolveSize(desired, widthMeasureSpec)
        val h = View.resolveSize(desired, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f

        val lift = (hoverAmount * dp(2.4f)) - (pressAmount * dp(1.8f))
        val radius = size / 2f - dp(4f) - expandAmount * dp(1f)
        val corner = when (shape) {
            Shape.CIRCLE -> radius
            Shape.ROUNDED_SQUARE -> radius * 0.42f
            Shape.CAPSULE -> radius
        }

        bodyBox.set(cx - radius, cy - radius + lift, cx + radius, cy + radius + lift)

        // 1 — ambient light spill
        val glowIntensity = (0.16f + 0.14f * breath + 0.22f * hoverAmount + 0.28f * expandAmount)
            .coerceIn(0f, 1f)
        if (glowIntensity > 0.02f && SkeuoTheme.shadowIntensity > 0.05f) {
            Skeuo.drawGlow(canvas, cx, cy + lift, radius * 1.55f, statusColor, glowIntensity)
        }

        // 2 — contact shadow
        val shadowStrength = (1f - pressAmount * 0.5f) * SkeuoTheme.shadowIntensity
        if (shadowStrength > 0.02f) {
            Skeuo.drawContactShadow(
                canvas, bodyBox, corner, Tint.alphaFraction(Color.BLACK, 0.92f),
                dp(7f) * shadowStrength + hoverAmount * dp(3f),
                dp(4f) * shadowStrength + hoverAmount * dp(2f),
            )
        }

        // 3 — machined bezel
        val bezelWidth = dp(3.2f) + expandAmount * dp(0.6f)
        bodyPaint.reset()
        bodyPaint.isAntiAlias = true
        bodyPaint.style = Paint.Style.STROKE
        bodyPaint.strokeWidth = bezelWidth
        bodyPaint.shader = Materials.rimShader(cx, cy + lift, radius, tokens)
        drawShape(canvas, bodyBox, corner, bodyPaint)

        // 4 — smoked glass body
        val bodyInset = bezelWidth / 2f
        val inner = innerBox
        Skeuo.inset(bodyBox, bodyInset, inner)
        bodyPaint.reset()
        bodyPaint.isAntiAlias = true
        val lightBias = 0.10f + hoverAmount * 0.10f - pressAmount * 0.06f
        val centerColor = Tint.lighten(tokens.metalMid, lightBias + 0.10f)
        val edgeColor = Tint.darken(tokens.surfaceInset, 0.10f - lightBias * 0.4f)
        bodyPaint.shader = RadialGradient(
            inner.centerX() - inner.width() * 0.22f,
            inner.centerY() - inner.height() * 0.30f,
            inner.width() * 1.15f,
            intArrayOf(
                Tint.lighten(centerColor, 0.16f),
                centerColor,
                edgeColor,
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawShape(canvas, inner, (corner - bodyInset).coerceAtLeast(0f), bodyPaint)

        // 5 — micro texture + inner shade
        bodyPaint.reset()
        bodyPaint.isAntiAlias = false
        val save = canvas.save()
        Skeuo.roundRectPath(SkeuoPathHolder.path, inner, (corner - bodyInset).coerceAtLeast(0f))
        canvas.clipPath(SkeuoPathHolder.path)
        bodyPaint.shader = obtainNoiseShader()
        bodyPaint.alpha = 26
        canvas.drawRect(inner, bodyPaint)
        canvas.restoreToCount(save)
        Skeuo.drawInnerShade(canvas, inner, corner - bodyInset, Tint.alphaFraction(Color.BLACK, 0.9f), dp(3.4f), density)

        // 6 — specular highlight
        bodyPaint.reset()
        bodyPaint.isAntiAlias = true
        val specTop = inner.top
        val specBottom = inner.top + inner.height() * (0.52f - pressAmount * 0.10f)
        bodyPaint.shader = android.graphics.LinearGradient(
            inner.left, specTop, inner.left, specBottom,
            Tint.alphaFraction(tokens.chromeHi, 0.24f + hoverAmount * 0.10f),
            Tint.alpha(tokens.chromeHi, 0),
            Shader.TileMode.CLAMP,
        )
        val saveSpec = canvas.save()
        canvas.clipPath(Skeuo.roundRectPath(SkeuoPathHolder.path, inner, (corner - bodyInset).coerceAtLeast(0f)))
        canvas.drawRect(inner.left, specTop, inner.right, specBottom, bodyPaint)
        canvas.restoreToCount(saveSpec)

        // 7 — status ring
        if (ringEnabled) {
            val ringRadius = radius + dp(2.2f) + expandAmount * dp(2.6f)
            ringPaint.strokeWidth = dp(2.4f) + expandAmount * dp(0.8f)
            val ringBox = RectF(cx - ringRadius, cy - ringRadius + lift, cx + ringRadius, cy + ringRadius + lift)
            // track
            ringPaint.color = Tint.alphaFraction(tokens.chromeLo, 0.35f)
            drawArcShape(canvas, ringBox, corner + dp(2.2f), 0f, 360f, ringPaint)
            // lit portion
            val sweep = 360f * activity.coerceIn(0.02f, 1f)
            ringPaint.color = Tint.mix(statusColor, Color.WHITE, hoverAmount * 0.25f)
            drawArcShape(canvas, ringBox, corner + dp(2.2f), -90f, sweep, ringPaint)
            if (expandAmount > 0.02f) {
                ringPaint.color = Tint.alphaFraction(statusColor, 0.35f * expandAmount)
                val halo = RectF()
                halo.set(ringBox)
                halo.inset(-dp(3f) * expandAmount, -dp(3f) * expandAmount)
                drawArcShape(canvas, halo, corner + dp(5f), 0f, 360f, ringPaint)
            }
        }

        // 8 — glyph
        val glyph = iconDrawable
        if (glyph != null) {
            val glyphSize = (radius * (0.62f - pressAmount * 0.06f + expandAmount * 0.04f)) * 2f * 0.5f
            val half = glyphSize / 2f
            iconRect.set(
                (cx - half).toInt(), (cy + lift - half).toInt(),
                (cx + half).toInt(), (cy + lift + half).toInt(),
            )
            glyph.bounds = iconRect
            glyph.setTint(Tint.mix(tokens.textPrimary, statusColor, 0.22f + expandAmount * 0.35f))
            val rotation = expandAmount * 45f + hoverAmount * 8f
            if (rotation > 0.1f) {
                val s = canvas.save()
                canvas.rotate(rotation, cx, cy + lift)
                glyph.draw(canvas)
                canvas.restoreToCount(s)
            } else {
                glyph.draw(canvas)
            }
        }

        // 9 — notification badge
        if (badge > 0) {
            val text = if (badge > 9) "9+" else badge.toString()
            badgePaint.reset()
            badgePaint.isAntiAlias = true
            badgePaint.textSize = sp(10f)
            badgePaint.textAlign = Paint.Align.CENTER
            badgePaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            val badgeRadius = dp(9f)
            val bx = bodyBox.right - badgeRadius * 0.55f
            val by = bodyBox.top + badgeRadius * 0.45f
            badgePaint.color = tokens.danger
            canvas.drawCircle(bx, by, badgeRadius, badgePaint)
            badgePaint.color = Tint.alphaFraction(Color.WHITE, 0.28f)
            canvas.drawCircle(bx, by - badgeRadius * 0.2f, badgeRadius * 0.72f, badgePaint)
            badgePaint.color = Color.WHITE
            canvas.drawText(
                text, bx,
                by - (badgePaint.descent() + badgePaint.ascent()) / 2f,
                badgePaint,
            )
        }

        drawFocusRing(canvas, bodyBox, corner)
    }

    private fun obtainNoiseShader(): Shader = noiseShader ?: android.graphics.BitmapShader(
        Textures.noise(size = 64, contrast = 12),
        Shader.TileMode.REPEAT, Shader.TileMode.REPEAT,
    ).also { noiseShader = it }

    private fun drawShape(canvas: Canvas, box: RectF, radius: Float, paint: Paint) {
        when (shape) {
            Shape.CIRCLE, Shape.CAPSULE -> canvas.drawOval(box, paint)
            Shape.ROUNDED_SQUARE -> canvas.drawRoundRect(box, radius, radius, paint)
        }
    }

    private fun drawArcShape(canvas: Canvas, box: RectF, radius: Float, start: Float, sweep: Float, paint: Paint) {
        if (sweep <= 0f) return
        when (shape) {
            Shape.CIRCLE, Shape.CAPSULE -> canvas.drawArc(box, start, sweep, false, paint)
            Shape.ROUNDED_SQUARE -> canvas.drawArc(box, start, sweep, false, paint)
        }
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        iconDrawable = ContextCompat.getDrawable(context, iconRes)
        noiseShader = null
        materialState.invalidate()
        stopBreathing()
        if (isAttachedToWindow) startBreathing()
        invalidate()
    }

    /** Convenience: maps a 0..100 load percentage + severity onto the ring. */
    fun updateTelemetry(loadPercent: Int, fpsRatio: Float, thermal: Int) {
        activity = (loadPercent / 100f).coerceIn(0f, 1f)
        status = when {
            thermal >= 3 -> Status.ERROR
            thermal == 2 -> Status.WARNING
            fpsRatio < 0.55f -> Status.WARNING
            fpsRatio > 0.9f -> Status.ACTIVE
            else -> Status.BUSY
        }
    }

    companion object {
        /** Precomputes the four anchor points used when snapping the orb to a screen corner. */
        fun cornerAnchors(): List<Pair<Float, Float>> = listOf(
            0.08f to 0.22f, 0.92f to 0.22f, 0.08f to 0.78f, 0.92f to 0.78f,
        )

        fun polar(cx: Float, cy: Float, radius: Float, angleDeg: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angleDeg.toDouble())
            return (cx + radius * cos(rad).toFloat()) to (cy + radius * sin(rad).toFloat())
        }
    }
}

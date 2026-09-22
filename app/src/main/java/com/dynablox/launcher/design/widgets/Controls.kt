package com.dynablox.launcher.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Finish
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoPathHolder
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Textures
import com.dynablox.launcher.design.Tint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Physical rocker switch: recessed rubber track, machined thumb with knurling, two status lamps.
 * Reports itself to accessibility services as a Switch and is operable with DPAD/Enter.
 */
class ToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var isChecked: Boolean = false
        private set

    var onCheckedChanged: ((Boolean) -> Unit)? = null

    var accentOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.success

    var a11yLabel: String? = null
        set(value) {
            field = value
            contentDescription = value
        }

    private var thumbProgress = 0f
    private var animator: ValueAnimator? = null
    private val track = RectF()
    private val thumb = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = a11yLabel

        val a = context.obtainStyledAttributes(attrs, R.styleable.ToggleSwitch)
        val label = a.getString(R.styleable.ToggleSwitch_dbxLabel)
        if (label != null) contentDescription = label
        if (a.hasValue(R.styleable.ToggleSwitch_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.ToggleSwitch_dbxAccentColor, tokens.success)
        }
        val initial = a.getBoolean(R.styleable.ToggleSwitch_dbxChecked, false)
        a.recycle()
        thumbProgress = if (initial) 1f else 0f
        isChecked = initial
    }

    fun setChecked(value: Boolean, notify: Boolean = false) {
        if (isChecked == value) return
        isChecked = value
        animateThumb(if (value) 1f else 0f)
        if (notify) onCheckedChanged?.invoke(value)
        invalidate()
    }

    override fun performClick(): Boolean {
        setChecked(!isChecked, true)
        tick()
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun animateThumb(target: Float) {
        animator?.cancel()
        if (Motion.duration(120L) == 0L) {
            thumbProgress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(thumbProgress, target).apply {
            duration = Motion.duration(220L)
            interpolator = Motion.expandInterpolator()
            addUpdateListener {
                thumbProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.resolveSize(dp(62f).toInt(), widthMeasureSpec),
            View.resolveSize(dp(32f).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        track.set(box)
        val radius = track.height() / 2f

        Materials.paint(
            canvas, track, radius, Material.RUBBER, tokens, materialState,
            MaterialOptions(specular = false, shadow = 1f, texture = true),
            density,
        )
        Skeuo.drawInnerShade(canvas, track, radius, Color.BLACK, dp(3f), density)

        // status lamps
        val lampRadius = dp(2.4f)
        val lampY = track.centerY()
        paint.reset()
        paint.isAntiAlias = true
        val offLampX = track.left + dp(11f)
        val onLampX = track.right - dp(11f)
        paint.color = if (thumbProgress < 0.5f) Tint.alphaFraction(tokens.textFaint, 0.5f) else Tint.alphaFraction(tokens.textFaint, 0.18f)
        canvas.drawCircle(offLampX, lampY, lampRadius, paint)
        val litColor = accentColor
        if (thumbProgress > 0.02f) {
            Skeuo.drawGlow(canvas, onLampX, lampY, lampRadius * 3.4f, litColor, thumbProgress * 0.8f)
        }
        paint.color = Tint.mix(Tint.alphaFraction(tokens.textFaint, 0.2f), litColor, thumbProgress)
        canvas.drawCircle(onLampX, lampY, lampRadius, paint)

        // thumb
        val thumbWidth = track.height() - dp(5f)
        val travel = track.width() - thumbWidth - dp(5f)
        val left = track.left + dp(2.5f) + travel * thumbProgress
        thumb.set(left, track.top + dp(2.5f), left + thumbWidth, track.bottom - dp(2.5f))
        val thumbRadius = thumb.height() / 2f

        Skeuo.drawContactShadow(
            canvas, thumb, thumbRadius, Tint.alphaFraction(Color.BLACK, 0.85f),
            dp(2f), dp(1f),
        )
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = Skeuo.faceGradient(thumb, Tint.lighten(tokens.metalLight, 0.18f), Tint.darken(tokens.metalMid, 0.22f))
        canvas.drawRoundRect(thumb, thumbRadius, thumbRadius, paint)
        paint.shader = null

        // Grip marking: knurling when machined, a thumb dimple when clay.
        Materials.drawGrip(canvas, thumb, thumbRadius, tokens, density)
        Materials.drawPartEdge(canvas, thumb, thumbRadius, tokens, density)
        // grip ridge
        paint.color = Tint.alphaFraction(Color.BLACK, 0.45f)
        paint.strokeWidth = dp(1.2f)
        canvas.drawLine(thumb.centerX(), thumb.top + dp(8f), thumb.centerX(), thumb.bottom - dp(8f), paint)
        paint.color = Tint.alphaFraction(Color.WHITE, 0.22f)
        canvas.drawLine(thumb.centerX() + dp(1.2f), thumb.top + dp(8f), thumb.centerX() + dp(1.2f), thumb.bottom - dp(8f), paint)

        // lit seam when on
        if (thumbProgress > 0.02f) {
            paint.color = Tint.alphaFraction(litColor, 0.55f * thumbProgress)
            paint.strokeWidth = dp(1.4f)
            val seam = RectF()
            Skeuo.inset(track, dp(1.4f), seam)
            canvas.drawRoundRect(seam, seam.height() / 2f, seam.height() / 2f, paint)
        }

        drawFocusRing(canvas, track, radius)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }
}

/**
 * Rotary encoder knob with a knurled skirt, detents and a value arc.
 *
 * Dragging rotates the knob (angle deltas accumulate, so a full range needs more than one turn if
 * the widget is small) and each detent produces a haptic click. DPAD keys step the value for
 * keyboard and switch-access users.
 */
class RotaryKnob @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var minValue: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var maxValue: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    var value: Float = 0f
        set(newValue) {
            val clamped = newValue.coerceIn(minValue, maxValue)
            if (abs(field - clamped) < 0.0001f) return
            field = clamped
            angle = fraction() * SWEEP - SWEEP / 2f
            contentDescription = buildA11y()
            invalidate()
        }

    /** 0 = continuous; otherwise the knob snaps to this many positions. */
    var detents: Int = 11
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }

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

    var onValueChanged: ((Float) -> Unit)? = null

    var accentOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.accent

    private var angle = 0f
    private var dragging = false
    private var lastTouchAngle = 0f
    private var lastDetent = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = RectF()
    private val arcBox = RectF()

    init {
        isClickable = true
        isFocusable = true
        val a = context.obtainStyledAttributes(attrs, R.styleable.RotaryKnob)
        minValue = a.getFloat(R.styleable.RotaryKnob_dbxValueMin, 0f)
        maxValue = a.getFloat(R.styleable.RotaryKnob_dbxValueMax, 1f)
        value = a.getFloat(R.styleable.RotaryKnob_dbxValue, minValue)
        labelText = a.getString(R.styleable.RotaryKnob_dbxLabel)
        unit = a.getString(R.styleable.RotaryKnob_dbxUnit)
        if (a.hasValue(R.styleable.RotaryKnob_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.RotaryKnob_dbxAccentColor, tokens.accent)
        }
        a.recycle()
        angle = fraction() * SWEEP - SWEEP / 2f
        contentDescription = buildA11y()
    }

    private fun fraction(): Float =
        if (maxValue <= minValue) 0f else ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    private fun buildA11y(): String {
        val label = labelText ?: ""
        val percent = (fraction() * 100f).roundToInt()
        return "$label $percent%${unit?.let { " $it" } ?: ""}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(76f).toInt()
        val desiredHeight = if (labelText != null) dp(94f).toInt() else desired
        setMeasuredDimension(
            View.resolveSize(desired, widthMeasureSpec),
            View.resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = knobCenterY()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastTouchAngle = touchAngle(event.x - cx, event.y - cy)
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val current = touchAngle(event.x - cx, event.y - cy)
                var delta = current - lastTouchAngle
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                lastTouchAngle = current
                rotateBy(delta)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun knobCenterY(): Float =
        if (labelText != null) (dp(76f)) / 2f + dp(2f) else height / 2f

    private fun touchAngle(dx: Float, dy: Float): Float =
        Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f

    private fun rotateBy(deltaDegrees: Float) {
        val next = (angle + deltaDegrees).coerceIn(-SWEEP / 2f, SWEEP / 2f)
        if (next == angle) return
        angle = next
        val raw = minValue + (angle + SWEEP / 2f) / SWEEP * (maxValue - minValue)
        val snapped = if (detents > 0) {
            val step = (maxValue - minValue) / (detents - 1).toFloat()
            minValue + ((raw - minValue) / step).roundToInt() * step
        } else raw
        val clamped = snapped.coerceIn(minValue, maxValue)
        if (detents > 0) {
            val index = ((clamped - minValue) / ((maxValue - minValue) / (detents - 1).toFloat())).roundToInt()
            if (index != lastDetent) {
                lastDetent = index
                tickLight()
            }
        }
        if (clamped != value) {
            value = clamped
            onValueChanged?.invoke(clamped)
        } else {
            invalidate()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = if (detents > 0) {
            (maxValue - minValue) / (detents - 1).toFloat()
        } else (maxValue - minValue) / 20f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                value = (value + step).coerceAtMost(maxValue)
                onValueChanged?.invoke(value)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> {
                value = (value - step).coerceAtLeast(minValue)
                onValueChanged?.invoke(value)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        val knobSize = min(width.toFloat(), dp(76f))
        val cx = width / 2f
        val cy = knobCenterY()
        val radius = knobSize / 2f - dp(3f)
        face.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // value arc
        val arcRadius = radius + dp(2.4f)
        arcBox.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.6f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Tint.alphaFraction(tokens.chromeLo, 0.35f)
        canvas.drawArc(arcBox, 90f + SWEEP / 2f, SWEEP, false, paint)
        paint.color = accentColor
        val filled = fraction() * SWEEP
        if (filled > 0.5f) canvas.drawArc(arcBox, 90f + SWEEP / 2f, filled, false, paint)

        // detent marks
        if (detents > 1) {
            paint.strokeWidth = dp(1f)
            paint.strokeCap = Paint.Cap.BUTT
            for (i in 0 until detents) {
                val a = Math.toRadians((90f + SWEEP / 2f + SWEEP * i / (detents - 1)).toDouble())
                val r1 = arcRadius + dp(2f)
                val r2 = arcRadius + dp(4.4f)
                paint.color = Tint.alphaFraction(tokens.textFaint, 0.4f)
                canvas.drawLine(
                    cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                    cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(),
                    paint,
                )
            }
        }

        // rubber skirt
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = Tint.darken(tokens.metalDark, 0.4f)
        canvas.drawCircle(cx, cy, radius, paint)
        // The skirt is the grip: machined knobs are knurled, clay knobs get finger ridges pressed
        // into the rim. Both communicate "this rotates"; only one is possible per material.
        if (SkeuoTheme.finish == Finish.MACHINED) {
            val knurl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.BitmapShader(
                    Textures.knurl(height = 32), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT,
                )
                alpha = 70
            }
            val save = canvas.save()
            canvas.clipRect(face)
            canvas.drawCircle(cx, cy, radius, knurl)
            canvas.restoreToCount(save)
        } else {
            val save = canvas.save()
            canvas.clipRect(face)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.6f)
            // Twelve shallow ridges around the rim, fading with the light direction.
            for (i in 0 until 12) {
                val a = Math.toRadians((i * 30f).toDouble())
                val lit = 0.5f + 0.5f * kotlin.math.cos(a - Math.toRadians(225.0)).toFloat()
                paint.color = Tint.alphaFraction(
                    if (lit > 0.5f) tokens.chromeHi else tokens.shadow,
                    0.05f + 0.10f * kotlin.math.abs(lit - 0.5f) * 2f,
                )
                val inner = radius * 0.86f
                canvas.drawLine(
                    cx + (inner * kotlin.math.cos(a)).toFloat(),
                    cy + (inner * kotlin.math.sin(a)).toFloat(),
                    cx + (radius * kotlin.math.cos(a)).toFloat(),
                    cy + (radius * kotlin.math.sin(a)).toFloat(),
                    paint,
                )
            }
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            canvas.restoreToCount(save)
        }

        // metal top
        val topRadius = radius * 0.80f
        val top = RectF(cx - topRadius, cy - topRadius, cx + topRadius, cy + topRadius)
        Skeuo.drawContactShadow(canvas, top, topRadius, Tint.alphaFraction(Color.BLACK, 0.9f), dp(2f), dp(1f))
        paint.shader = android.graphics.RadialGradient(
            cx - topRadius * 0.3f, cy - topRadius * 0.35f, topRadius * 1.7f,
            intArrayOf(Tint.lighten(tokens.metalLight, 0.22f), tokens.metalMid, Tint.darken(tokens.metalDark, 0.3f)),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, topRadius, paint)
        paint.shader = null
        Materials.drawPartEdge(canvas, top, topRadius, tokens, density)

        // pointer
        val pointerAngle = Math.toRadians((angle - 90f).toDouble())
        val p1 = topRadius * 0.34f
        val p2 = topRadius * 0.86f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(3f)
        paint.color = Tint.alphaFraction(Color.BLACK, 0.55f)
        canvas.drawLine(
            cx + p1 * cos(pointerAngle).toFloat(), cy + p1 * sin(pointerAngle).toFloat(),
            cx + p2 * cos(pointerAngle).toFloat(), cy + p2 * sin(pointerAngle).toFloat(),
            paint,
        )
        paint.strokeWidth = dp(1.8f)
        paint.color = Tint.mix(accentColor, Color.WHITE, 0.25f)
        canvas.drawLine(
            cx + p1 * cos(pointerAngle).toFloat(), cy + p1 * sin(pointerAngle).toFloat(),
            cx + p2 * cos(pointerAngle).toFloat(), cy + p2 * sin(pointerAngle).toFloat(),
            paint,
        )

        // centre dimple
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = Tint.darken(tokens.metalMid, 0.35f)
        canvas.drawCircle(cx, cy, dp(3.4f), paint)
        paint.color = Tint.alphaFraction(Color.WHITE, 0.18f)
        canvas.drawCircle(cx - dp(0.9f), cy - dp(1f), dp(1.3f), paint)

        // label + value
        val label = labelText
        if (label != null) {
            labelPaint.textSize = sp(9f)
            labelPaint.color = tokens.textFaint
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(label.uppercase(), cx, cy + radius + dp(14f), labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
        }

        drawFocusRing(canvas, face, radius)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }

    companion object {
        /** Total travel of the knob, in degrees. */
        const val SWEEP = 270f
    }
}

/**
 * Console fader: recessed slot with an engraved scale, a brushed metal cap and an accent fill.
 * Supports drag, tap-to-jump and DPAD keys.
 */
class FaderSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var minValue: Float = 0f
    var maxValue: Float = 1f

    var value: Float = 0f
        set(newValue) {
            val clamped = newValue.coerceIn(minValue, maxValue)
            if (abs(field - clamped) < 0.0001f) return
            field = clamped
            contentDescription = buildA11y()
            invalidate()
        }

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

    var onValueChanged: ((Float) -> Unit)? = null

    var accentOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.accent

    private var dragging = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slot = RectF()
    private val cap = RectF()

    init {
        isClickable = true
        isFocusable = true
        val a = context.obtainStyledAttributes(attrs, R.styleable.FaderSlider)
        minValue = a.getFloat(R.styleable.FaderSlider_dbxValueMin, 0f)
        maxValue = a.getFloat(R.styleable.FaderSlider_dbxValueMax, 1f)
        labelText = a.getString(R.styleable.FaderSlider_dbxLabel)
        unit = a.getString(R.styleable.FaderSlider_dbxUnit)
        if (a.hasValue(R.styleable.FaderSlider_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.FaderSlider_dbxAccentColor, tokens.accent)
        }
        value = a.getFloat(R.styleable.FaderSlider_dbxValue, minValue)
        a.recycle()
        contentDescription = buildA11y()
    }

    private fun fraction(): Float =
        if (maxValue <= minValue) 0f else ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    private fun buildA11y(): String {
        val percent = (fraction() * 100f).roundToInt()
        return "${labelText ?: ""} $percent%${unit?.let { " $it" } ?: ""}".trim()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.resolveSize(dp(200f).toInt(), widthMeasureSpec),
            View.resolveSize(dp(44f).toInt(), heightMeasureSpec),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                applyFromX(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) applyFromX(event.x)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyFromX(x: Float) {
        val capWidth = dp(22f)
        val left = dp(14f) + capWidth / 2f
        val right = width - dp(14f) - capWidth / 2f
        val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val next = minValue + fraction * (maxValue - minValue)
        if (next != value) {
            value = next
            onValueChanged?.invoke(next)
            tickLight()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = (maxValue - minValue) / 20f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> {
                value = (value + step).coerceAtMost(maxValue)
                onValueChanged?.invoke(value)
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                value = (value - step).coerceAtLeast(minValue)
                onValueChanged?.invoke(value)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        val capWidth = dp(22f)
        val capHeight = dp(28f)
        val left = dp(14f)
        val right = width - dp(14f)
        val centerY = height / 2f + (if (labelText != null) dp(4f) else 0f)

        // recessed slot
        slot.set(left, centerY - dp(4f), right, centerY + dp(4f))
        val slotRadius = slot.height() / 2f
        Materials.paint(
            canvas, slot, slotRadius, Material.INSET, tokens, materialState,
            MaterialOptions(specular = false, bevel = false, shadow = 1f),
            density,
        )

        // engraved scale
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeWidth = dp(1f)
        paint.color = Tint.alphaFraction(tokens.textFaint, 0.35f)
        for (i in 0..10) {
            val x = left + (right - left) * i / 10f
            val tickHeight = if (i % 5 == 0) dp(5f) else dp(3f)
            canvas.drawLine(x, centerY - dp(9f), x, centerY - dp(9f) + tickHeight, paint)
        }

        // accent fill
        val travel = right - left - capWidth
        val capLeft = left + capWidth / 2f + travel * fraction()
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = Tint.alphaFraction(accentColor, 0.75f)
        val fill = RectF(left + dp(2f), centerY - dp(2f), capLeft, centerY + dp(2f))
        canvas.drawRoundRect(fill, dp(2f), dp(2f), paint)
        Skeuo.drawGlow(canvas, capLeft, centerY, dp(12f), accentColor, 0.45f)

        // brushed cap
        cap.set(capLeft - capWidth / 2f, centerY - capHeight / 2f, capLeft + capWidth / 2f, centerY + capHeight / 2f)
        val capRadius = dp(4f)
        Skeuo.drawContactShadow(canvas, cap, capRadius, Tint.alphaFraction(Color.BLACK, 0.9f), dp(2.5f), dp(1.5f))
        paint.shader = Skeuo.faceGradient(cap, Tint.lighten(tokens.metalLight, 0.2f), Tint.darken(tokens.metalMid, 0.3f))
        canvas.drawRoundRect(cap, capRadius, capRadius, paint)
        paint.shader = null
        Materials.drawGrip(canvas, cap, capRadius, tokens, density)
        Materials.drawPartEdge(canvas, cap, capRadius, tokens, density)
        // centre groove
        paint.color = Tint.alphaFraction(Color.BLACK, 0.5f)
        paint.strokeWidth = dp(1.4f)
        canvas.drawLine(cap.centerX(), cap.top + dp(6f), cap.centerX(), cap.bottom - dp(6f), paint)
        paint.color = Tint.alphaFraction(accentColor, 0.9f)
        paint.strokeWidth = dp(1f)
        canvas.drawLine(cap.centerX(), cap.top + dp(6f), cap.centerX(), cap.bottom - dp(6f), paint)

        val label = labelText
        if (label != null) {
            labelPaint.textSize = sp(9f)
            labelPaint.color = tokens.textFaint
            canvas.drawText(label.uppercase(), left, centerY - dp(13f), labelPaint)
            val valueText = "${(fraction() * 100f).roundToInt()}${unit ?: ""}"
            valuePaintRight(labelPaint, valueText, right, centerY - dp(13f), canvas)
        }

        drawFocusRing(canvas, slot, slotRadius)
    }

    private fun valuePaintRight(paint: Paint, text: String, right: Float, baseline: Float, canvas: Canvas) {
        paint.textAlign = Paint.Align.RIGHT
        paint.color = tokens.textSecondary
        canvas.drawText(text, right, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        materialState.invalidate()
        invalidate()
    }
}

package com.dynablox.launcher.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.MaterialState
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.Tint
import com.dynablox.launcher.di.AppContainer

/**
 * One physical-looking pad key: rubber cap, chrome bezel, engraved glyph, LED ring while held.
 *
 * It never draws a fake joystick — it draws a key, and pressing it injects a real gesture at the
 * coordinates the preset maps to on the game screen.
 */
class PadKey(
    context: Context,
    private val button: PadButton,
    private val onPress: (PadButton) -> Unit,
) : View(context) {

    private var tokens: ThemeTokens = SkeuoTheme.tokens(context)
    private val state = MaterialState()
    private val box = RectF()
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val glyph = Path()
    private var pressed = 0f
    private var pressAnimator: android.animation.ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    var keyOpacity: Float = 1f
        set(value) {
            field = value.coerceIn(0.15f, 1f)
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(button.labelRes)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = Dimens.dp(context, 3f)
        box.set(inset, inset, w - inset, h - inset)
        state.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = box.width() / 2f
        val sink = pressed * Dimens.dp(context, 1.6f)
        val cap = RectF(box)
        cap.inset(sink, sink)
        cap.offset(0f, sink * 0.6f)

        Materials.paint(
            canvas, cap, radius, if (pressed > 0.4f) Material.INSET else Material.RUBBER, tokens, state,
            MaterialOptions(
                specular = pressed < 0.4f,
                bevel = true,
                shadow = (1f - pressed * 0.7f) * keyOpacity,
                opacity = keyOpacity,
            ),
            resources.displayMetrics.density,
        )

        // chrome bezel
        capPaint.reset()
        capPaint.isAntiAlias = true
        capPaint.style = Paint.Style.STROKE
        capPaint.strokeWidth = Dimens.dp(context, 1.2f)
        capPaint.color = Tint.alphaFraction(tokens.chromeHi, 0.30f * keyOpacity)
        canvas.drawCircle(cap.centerX(), cap.centerY(), radius - Dimens.dp(context, 1.4f), capPaint)

        // LED ring while held
        if (pressed > 0.05f) {
            capPaint.strokeWidth = Dimens.dp(context, 2f)
            capPaint.color = Tint.alphaFraction(tokens.accent, 0.85f * pressed * keyOpacity)
            canvas.drawCircle(cap.centerX(), cap.centerY(), radius - Dimens.dp(context, 0.6f), capPaint)
        }

        drawGlyph(canvas, cap)

        textPaint.color = Tint.alphaFraction(tokens.textPrimary, (0.92f - pressed * 0.1f) * keyOpacity)
        textPaint.textSize = Dimens.sp(context, 9.5f)
        val label = context.getString(button.labelRes).uppercase()
        val text = if (textPaint.measureText(label) > cap.width() * 0.82f) {
            label.take(3)
        } else {
            label
        }
        canvas.drawText(
            text,
            cap.centerX(),
            cap.centerY() + Dimens.dp(context, if (hasGlyph()) 9f else 3.4f),
            textPaint,
        )

        if (isFocused) {
            capPaint.reset()
            capPaint.isAntiAlias = true
            capPaint.style = Paint.Style.STROKE
            capPaint.strokeWidth = Dimens.dp(context, 2f)
            capPaint.color = Tint.alphaFraction(tokens.accent, 0.95f)
            canvas.drawCircle(cap.centerX(), cap.centerY(), radius + Dimens.dp(context, 2f), capPaint)
        }
    }

    private fun hasGlyph(): Boolean = when (button.gesture) {
        PadGesture.SWIPE_UP, PadGesture.SWIPE_DOWN, PadGesture.SWIPE_LEFT, PadGesture.SWIPE_RIGHT -> true
        else -> false
    }

    private fun drawGlyph(canvas: Canvas, cap: RectF) {
        val direction = when (button.gesture) {
            PadGesture.SWIPE_UP -> 0f
            PadGesture.SWIPE_RIGHT -> 90f
            PadGesture.SWIPE_DOWN -> 180f
            PadGesture.SWIPE_LEFT -> 270f
            else -> return
        }
        glyph.reset()
        val size = Dimens.dp(context, 7f)
        val cy = cap.centerY() - Dimens.dp(context, 6f)
        glyph.moveTo(cap.centerX(), cy - size)
        glyph.lineTo(cap.centerX() + size, cy + size * 0.4f)
        glyph.lineTo(cap.centerX() - size, cy + size * 0.4f)
        glyph.close()
        glyph.transform(
            android.graphics.Matrix().apply {
                setRotate(direction, cap.centerX(), cy)
            },
        )
        capPaint.reset()
        capPaint.isAntiAlias = true
        capPaint.style = Paint.Style.FILL
        capPaint.color = Tint.alphaFraction(tokens.accent, (0.75f + pressed * 0.25f) * keyOpacity)
        canvas.drawPath(glyph, capPaint)
    }

    private fun setPressedVisual(value: Boolean) {
        pressAnimator?.cancel()
        val target = if (value) 1f else 0f
        if (Motion.duration(90L) == 0L) {
            pressed = target
            invalidate()
            return
        }
        pressAnimator = android.animation.ValueAnimator.ofFloat(pressed, target).apply {
            duration = Motion.duration(if (value) 70L else 150L)
            interpolator = Motion.interpolator()
            addUpdateListener {
                pressed = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        setPressedVisual(pressed)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = true
                isPressed = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking && (Math.abs(event.x - downX) > slop * 2f || Math.abs(event.y - downY) > slop * 2f)) {
                    tracking = false
                    isPressed = false
                }
            }

            MotionEvent.ACTION_UP -> {
                if (tracking) {
                    isPressed = false
                    performClick()
                    onPress(button)
                }
                tracking = false
            }

            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                isPressed = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        state.invalidate()
        invalidate()
    }
}

/**
 * A draggable cluster of pad keys (left or right thumb zone).
 *
 * Each side lives in its own overlay window sized exactly to the cluster, so touches anywhere else
 * on the screen still reach the game — a full-screen pad window would eat them.
 */
class PadClusterView(
    context: Context,
    container: AppContainer,
    val side: ClusterSide,
) : FrameLayout(SkeuoTheme.wrap(context, container.settings)) {

    private val settings = container.settings
    private val themed = SkeuoTheme.wrap(context, settings)
    private val keys = mutableListOf<PadKey>()
    private var minWidthPx = 0
    private var minHeightPx = 0

    var onKey: ((PadButton) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        rebuild()
    }

    fun preset(): ControlPreset = ControlPresets.byId(settings.controlsPresetId)

    fun buttons(): List<PadButton> =
        preset().clusters.firstOrNull { it.side == side }?.buttons ?: emptyList()

    fun rebuild() {
        removeAllViews()
        keys.clear()
        val scale = settings.controlsScale.coerceIn(0.6f, 1.8f)
        val density = resources.displayMetrics.density * Dimens.factor * scale
        var maxRight = 0f
        var maxBottom = 0f
        buttons().forEach { button ->
            val size = (button.sizeDp * density).toInt()
            val x = (button.localX * clusterWidth(density)).toInt() - size / 2
            val y = (button.localY * clusterHeight(density)).toInt() - size / 2
            val key = PadKey(themed, button) { fired -> onKey?.invoke(fired) }
            key.keyOpacity = settings.controlsOpacity.coerceIn(0.2f, 1f)
            addView(key, FrameLayout.LayoutParams(size, size).apply {
                leftMargin = x.coerceAtLeast(0)
                topMargin = y.coerceAtLeast(0)
            })
            keys.add(key)
            maxRight = maxOf(maxRight, (x + size).toFloat())
            maxBottom = maxOf(maxBottom, (y + size).toFloat())
        }
        minWidthPx = (maxRight + Dimens.dp(context, 4f)).toInt()
        minHeightPx = (maxBottom + Dimens.dp(context, 4f)).toInt()
        requestLayout()
    }

    private fun clusterWidth(density: Float): Float = 132f * density
    private fun clusterHeight(density: Float): Float = 132f * density

    fun applyOpacity() {
        val alpha = settings.controlsOpacity.coerceIn(0.2f, 1f)
        keys.forEach { it.keyOpacity = alpha }
    }

    /** Measures the cluster so the host service can size its window exactly. */
    fun desiredSize(): Pair<Int, Int> {
        measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return maxOf(measuredWidth, minWidthPx) to maxOf(measuredHeight, minHeightPx)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = maxOf(measuredWidth, minWidthPx)
        val h = maxOf(measuredHeight, minHeightPx)
        if (w != measuredWidth || h != measuredHeight) setMeasuredDimension(w, h)
    }

    fun refreshTheme() {
        keys.forEach { it.refreshTheme() }
    }

    fun release() {
        removeAllViews()
        keys.clear()
    }
}

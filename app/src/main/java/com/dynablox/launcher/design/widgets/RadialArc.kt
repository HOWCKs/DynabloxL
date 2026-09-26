package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Tint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radial satellite container.
 *
 * Children (usually [IconTile]s) are laid out on an arc around the centre of this view — which the
 * MenuHub aligns with the orb — so the primary commands physically hang off the knob they belong to.
 * [progress] drives the whole expansion: radius, scale, alpha and a per-child stagger, giving the
 * arc its "unfolding" feel without a single bitmap.
 */
class RadialArc @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr), SkeuoComponent {

    /** Centre of the arc, in degrees. 270 = above the orb, 90 = below. */
    var centerAngle: Float = 270f

    /** Total spread of the arc, in degrees. */
    var spread: Float = 168f

    /** Distance from the centre to each child's centre, in px. */
    var radius: Float = 0f

    /** 0 = collapsed into the orb, 1 = fully deployed. */
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                requestLayout()
                invalidate()
            }
        }

    private var tokens = SkeuoTheme.tokens(context)
    private val hingePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcBox = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val childSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(childSpec, childSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (progress <= 0.001f) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.alpha = 0f
                child.layout(0, 0, 0, 0)
            }
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val visible = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }
        if (visible.isEmpty()) return

        val count = visible.size
        val startAngle = centerAngle - spread / 2f
        val step = if (count == 1) 0f else spread / (count - 1)

        visible.forEachIndexed { index, child ->
            val stagger = (1f - (index * 0.06f)).coerceIn(0.55f, 1f)
            val local = (progress * stagger).coerceIn(0f, 1f)
            val eased = Motion.expandInterpolator().getInterpolation(local)
            val angle = Math.toRadians((startAngle + step * index).toDouble())
            val childRadius = radius * eased
            val px = cx + childRadius * cos(angle).toFloat()
            val py = cy + childRadius * sin(angle).toFloat()
            val w = child.measuredWidth
            val h = child.measuredHeight
            child.layout((px - w / 2f).toInt(), (py - h / 2f).toInt(), (px + w / 2f).toInt(), (py + h / 2f).toInt())
            child.alpha = eased
            child.scaleX = 0.62f + 0.38f * eased
            child.scaleY = 0.62f + 0.38f * eased
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (progress > 0.05f && radius > 0f) drawHinge(canvas)
        super.dispatchDraw(canvas)
    }

    /** The machined hinge ring that visually connects the satellites to the orb. */
    private fun drawHinge(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val eased = Motion.expandInterpolator().getInterpolation(progress)
        val r = radius * eased
        arcBox.set(cx - r, cy - r, cx + r, cy + r)
        hingePaint.strokeWidth = Skeuo.dp(context, 1.6f)
        hingePaint.color = Tint.alphaFraction(tokens.chromeLo, 0.45f * eased)
        canvas.drawArc(arcBox, centerAngle - spread / 2f, spread, false, hingePaint)

        hingePaint.strokeWidth = Skeuo.dp(context, 0.8f)
        hingePaint.color = Tint.alphaFraction(tokens.chromeHi, 0.18f * eased)
        arcBox.inset(-Skeuo.dp(context, 2f), -Skeuo.dp(context, 2f))
        canvas.drawArc(arcBox, centerAngle - spread / 2f, spread, false, hingePaint)

        // spokes
        hingePaint.strokeWidth = Skeuo.dp(context, 1f)
        hingePaint.color = Tint.alphaFraction(tokens.accent, 0.22f * eased)
        val visible = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }
        val count = visible.size
        if (count == 0) return
        val startAngle = centerAngle - spread / 2f
        val step = if (count == 1) 0f else spread / (count - 1)
        for (i in 0 until count) {
            val angle = Math.toRadians((startAngle + step * i).toDouble())
            val inner = Skeuo.dp(context, 34f)
            canvas.drawLine(
                cx + inner * cos(angle).toFloat(), cy + inner * sin(angle).toFloat(),
                cx + (r - Skeuo.dp(context, 26f)) * cos(angle).toFloat(),
                cy + (r - Skeuo.dp(context, 26f)) * sin(angle).toFloat(),
                hingePaint,
            )
        }
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        (0 until childCount).forEach { (getChildAt(it) as? SkeuoComponent)?.refreshTheme() }
        invalidate()
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = FrameLayout.LayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p != null
}

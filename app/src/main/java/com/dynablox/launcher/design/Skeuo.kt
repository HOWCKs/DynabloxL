package com.dynablox.launcher.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.roundToInt

/**
 * Low level material primitives shared by every skeuomorphic widget: bevels, chamfers, inner
 * shading, specular reflections, contact shadows and chrome rings.
 *
 * All helpers avoid `BlurMaskFilter` on purpose — it is ignored by hardware accelerated canvases
 * on API 26/27, so soft shading is built from layered strokes and gradients instead. That keeps
 * the materials identical on every supported device.
 */
object Skeuo {

    private val rect = RectF()
    private val path = Path()

    fun roundRectPath(target: Path, box: RectF, radius: Float): Path {
        target.reset()
        val r = radius.coerceAtLeast(0f)
        target.addRoundRect(box, r, r, Path.Direction.CW)
        return target
    }

    fun inset(box: RectF, amount: Float, out: RectF = RectF()): RectF = out.apply {
        set(box.left + amount, box.top + amount, box.right - amount, box.bottom - amount)
    }

    /** Vertical body gradient — the base of metal, ceramic and glass faces. */
    fun faceGradient(box: RectF, top: Int, bottom: Int): LinearGradient = LinearGradient(
        box.left, box.top, box.left, box.bottom, top, bottom, Shader.TileMode.CLAMP,
    )

    /** Diagonal gradient used for chamfered bezels. */
    fun bevelGradient(box: RectF, highlight: Int, shade: Int): LinearGradient = LinearGradient(
        box.left, box.top, box.right, box.bottom,
        intArrayOf(highlight, shade, highlight, shade),
        floatArrayOf(0f, 0.42f, 0.58f, 1f),
        Shader.TileMode.CLAMP,
    )

    /** Polished ring: a sweep gradient that fakes a lit chrome bezel. */
    fun chromeSweep(cx: Float, cy: Float, radius: Float, hi: Int, lo: Int): SweepGradient =
        SweepGradient(
            cx, cy,
            intArrayOf(lo, hi, lo, hi, lo),
            floatArrayOf(0f, 0.18f, 0.5f, 0.82f, 1f),
        )

    fun drawBody(canvas: Canvas, box: RectF, radius: Float, paint: Paint) {
        canvas.drawRoundRect(box, radius, radius, paint)
    }

    /**
     * Chamfer: a thin lit edge on the top-left, a thin dark edge on the bottom-right. This single
     * pass is what makes a flat rounded rect read as a machined object.
     */
    fun drawBevel(canvas: Canvas, box: RectF, radius: Float, hi: Int, lo: Int, widthDp: Float, density: Float) {
        val w = (widthDp * density).coerceAtLeast(0.6f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            shader = bevelGradient(box, hi, lo)
        }
        inset(box, w / 2f, rect)
        val r = (radius - w / 2f).coerceAtLeast(0f)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.shader = null
    }

    /** Hairline separation between stacked surfaces. */
    fun drawHairline(canvas: Canvas, box: RectF, radius: Float, color: Int, widthDp: Float, density: Float) {
        val w = (widthDp * density).coerceAtLeast(0.5f)
        val lineColor = color
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            this.color = lineColor
        }
        inset(box, w / 2f, rect)
        val r = (radius - w / 2f).coerceAtLeast(0f)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    /**
     * Soft inner shading, built from three concentric strokes clipped to the shape. Reads as a
     * recessed well (inset panels, LCD glass, rubber tracks).
     */
    fun drawInnerShade(canvas: Canvas, box: RectF, radius: Float, color: Int, depthDp: Float, density: Float) {
        val depth = depthDp * density
        if (depth <= 0.1f) return
        val save = canvas.save()
        roundRectPath(path, box, radius)
        canvas.clipPath(path)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val layers = 3
        for (i in 0 until layers) {
            val fraction = 1f - i / layers.toFloat()
            val w = depth * fraction * 1.6f
            paint.strokeWidth = w
            paint.color = Tint.alphaFraction(color, 0.34f * fraction)
            inset(box, w * 0.35f, rect)
            val r = (radius - w * 0.35f).coerceAtLeast(0f)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        canvas.restoreToCount(save)
    }

    /** Glossy top reflection — the "glass lid" of a ceramic or smoked-glass surface. */
    fun drawSpecular(canvas: Canvas, box: RectF, radius: Float, color: Int, heightFraction: Float = 0.45f) {
        val save = canvas.save()
        roundRectPath(path, box, radius)
        canvas.clipPath(path)
        val bottom = box.top + box.height() * heightFraction
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                box.left, box.top, box.left, bottom,
                color, Tint.alpha(color, 0), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(box.left, box.top, box.right, bottom, paint)
        canvas.restoreToCount(save)
    }

    /** Contact shadow painted under floating objects (orb, tiles, panels). */
    fun drawContactShadow(canvas: Canvas, box: RectF, radius: Float, color: Int, offsetY: Float, spread: Float) {
        val save = canvas.save()
        val cx = box.centerX()
        val cy = box.centerY() + offsetY
        val rx = box.width() / 2f + spread
        val ry = box.height() / 2f + spread
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, maxOf(rx, ry),
                intArrayOf(Tint.alphaFraction(color, 0.55f), Tint.alpha(color, 0)),
                floatArrayOf(0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.translate(cx, cy)
        canvas.scale(1f, (ry / rx).coerceIn(0.2f, 1f))
        canvas.translate(-cx, -cy)
        canvas.drawCircle(cx, cy, rx, paint)
        canvas.restoreToCount(save)
    }

    /** Ambient glow used by status rings and LEDs. */
    fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, intensity: Float) {
        if (intensity <= 0.01f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(Tint.alphaFraction(color, 0.75f * intensity), Tint.alpha(color, 0)),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density * Dimens.factor

    fun dpInt(context: Context, value: Float): Int = dp(context, value).roundToInt()
}

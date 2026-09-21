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
 * Tactile icon key used by the radial menu, the launcher deck and the settings lists.
 *
 * Ceramic face, chamfered edge, top highlight, monoline glyph slightly proud of the surface,
 * optional engraved label and a favourite pin. Pressing sinks the key and dims the glyph.
 */
class IconTile @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SkeuoWidget(context, attrs, defStyleAttr), SkeuoComponent {

    var iconRes: Int = 0
        set(value) {
            field = value
            iconDrawable = if (value == 0) null else ContextCompat.getDrawable(context, value)
            invalidate()
        }

    var label: String? = null
        set(value) {
            field = value
            contentDescription = value ?: contentDescription
            requestLayout()
            invalidate()
        }

    var showLabel: Boolean = true
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var favorite: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var accentOverride: Int? = null
        set(value) {
            field = value
            materialState.invalidate()
            invalidate()
        }

    val accentColor: Int get() = accentOverride ?: tokens.accent

    /** 0..1 highlight blend used by the radial menu to mark the armed slot. */
    var armed: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var material: Material = Material.CERAMIC
        set(value) {
            field = value
            materialState.invalidate()
            invalidate()
        }

    private var iconDrawable: Drawable? = null
    private var press = 0f
    private var pressAnimator: ValueAnimator? = null
    private val face = RectF()
    private val glyph = Rect()
    private val starGlyph = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var starIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_star)

    init {
        isClickable = true
        isFocusable = true

        val a = context.obtainStyledAttributes(attrs, R.styleable.IconTile)
        label = a.getString(R.styleable.IconTile_dbxLabel)
        iconRes = a.getResourceId(R.styleable.IconTile_dbxIcon, 0)
        showLabel = a.getBoolean(R.styleable.IconTile_dbxShowLabel, true)
        if (a.hasValue(R.styleable.IconTile_dbxAccentColor)) {
            accentOverride = a.getColor(R.styleable.IconTile_dbxAccentColor, tokens.accent)
        }
        a.recycle()
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        pressAnimator?.cancel()
        val target = if (pressed) 1f else 0f
        if (Motion.duration(120L) == 0L) {
            press = target
            invalidate()
        } else {
            pressAnimator = ValueAnimator.ofFloat(press, target).apply {
                duration = Motion.duration(if (pressed) 90L else 220L)
                interpolator = if (pressed) Motion.precise else Motion.spring
                addUpdateListener {
                    press = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        if (pressed) tickLight()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(64f).toInt()
        val desiredHeight = if (showLabel && label != null) dp(84f).toInt() else desired
        setMeasuredDimension(
            View.resolveSize(maxOf(desired, minimumWidth), widthMeasureSpec),
            View.resolveSize(maxOf(desiredHeight, minimumHeight), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width.toFloat(), if (showLabel && label != null) height - dp(22f) else height.toFloat())
        if (size <= 0f) return
        val sink = dp(1.4f) * press
        val left = (width - size) / 2f
        val top = dp(3f) + sink - armed * dp(1.5f)
        face.set(left, top, left + size, top + size)
        val radius = size * 0.30f

        val shadowStrength = (1f - press * 0.6f) * SkeuoTheme.shadowIntensity
        if (shadowStrength > 0.02f) {
            Skeuo.drawContactShadow(
                canvas, face, radius, Tint.alphaFraction(Color.BLACK, 0.85f),
                dp(4f) * shadowStrength + armed * dp(2f),
                dp(2.5f) * shadowStrength + armed * dp(2f),
            )
        }
        if (armed > 0.02f) {
            Skeuo.drawGlow(canvas, face.centerX(), face.centerY(), size * 0.8f, accentColor, armed * 0.4f)
        }

        Materials.paint(
            canvas, face, radius, material, tokens, materialState,
            MaterialOptions(
                shadow = 1f - press * 0.4f,
                tint = if (armed > 0.02f || isSelected || isActivated) accentColor else null,
                tintAmount = (0.10f + armed * 0.22f).coerceIn(0f, 1f),
                specular = press < 0.9f,
            ),
            density,
        )
        if (press > 0.01f) {
            Skeuo.drawInnerShade(canvas, face, radius, tokens.shadow, dp(3f) * press, density)
        }

        val icon = iconDrawable
        if (icon != null) {
            val glyphSize = size * (0.46f - press * 0.03f)
            val gl = face.centerX() - glyphSize / 2f
            val gt = face.centerY() - glyphSize / 2f - dp(if (showLabel && label != null) 0f else 0f)
            glyph.set(gl.toInt(), gt.toInt(), (gl + glyphSize).toInt(), (gt + glyphSize).toInt())
            icon.bounds = glyph
            icon.setTint(
                if (armed > 0.4f || isSelected || isActivated) {
                    Tint.mix(tokens.textPrimary, accentColor, 0.55f)
                } else {
                    tokens.iconTint
                },
            )
            icon.draw(canvas)
        }

        val text = label
        if (text != null && showLabel) {
            labelPaint.textSize = sp(10.5f)
            labelPaint.color = if (armed > 0.4f) accentColor else tokens.textSecondary
            labelPaint.textAlign = Paint.Align.CENTER
            val maxTextWidth = width - dp(4f)
            val ellipsized = ellipsize(text, labelPaint, maxTextWidth)
            canvas.drawText(
                ellipsized, width / 2f,
                face.bottom + dp(15f),
                labelPaint,
            )
            labelPaint.textAlign = Paint.Align.LEFT
        }

        if (favorite) drawPin(canvas)

        drawFocusRing(canvas, face, radius)
    }

    private fun drawPin(canvas: Canvas) {
        val star = starIcon ?: return
        val size = dp(13f)
        val l = face.right - size * 0.72f
        val t = face.top - size * 0.28f
        starGlyph.set(l.toInt(), t.toInt(), (l + size).toInt(), (t + size).toInt())
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Tint.alphaFraction(Color.BLACK, 0.55f)
        canvas.drawCircle(starGlyph.centerX(), starGlyph.centerY(), size * 0.62f, paint)
        paint.color = tokens.metalLight
        canvas.drawCircle(starGlyph.centerX(), starGlyph.centerY(), size * 0.56f, paint)
        star.bounds = starGlyph
        star.setTint(tokens.warning)
        star.draw(canvas)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end - 1) + "…") > maxWidth) end--
        return text.substring(0, (end - 1).coerceAtLeast(0)) + "…"
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        starIcon = ContextCompat.getDrawable(context, R.drawable.ic_star)
        if (iconRes != 0) iconDrawable = ContextCompat.getDrawable(context, iconRes)
        materialState.invalidate()
        invalidate()
    }
}

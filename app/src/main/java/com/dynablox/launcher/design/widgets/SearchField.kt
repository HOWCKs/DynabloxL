package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.MaterialState
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.Skeuo
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.Tint

/**
 * Recessed search field: smoked-glass well, engraved magnifier, luminous focus ring and a physical
 * clear key. It is a real EditText, so IME, selection and keyboard navigation all work unchanged.
 */
class SearchField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr), SkeuoComponent {

    var onClear: (() -> Unit)? = null

    private var tokens: ThemeTokens = SkeuoTheme.tokens(context)
    private val state = MaterialState()
    private val well = RectF()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconBounds = Rect()
    private val clearBounds = Rect()
    private val searchIcon = ContextCompat.getDrawable(context, R.drawable.ic_search)
    private val clearIcon = ContextCompat.getDrawable(context, R.drawable.ic_close)
    private var density = resources.displayMetrics.density

    init {
        background = null
        isSingleLine = true
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setHintTextColor(tokens.textFaint)
        setTextColor(tokens.textPrimary)
        textSize = 14f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        if (hint == null) hint = context.getString(R.string.menuhub_search_hint)
        val padLeft = Skeuo.dp(context, 38f).toInt()
        val padRight = Skeuo.dp(context, 36f).toInt()
        val padV = Skeuo.dp(context, 12f).toInt()
        setPadding(padLeft, padV, padRight, padV)
        minimumHeight = Skeuo.dp(context, 48f).toInt()
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = context.getString(R.string.cd_search)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        well.set(0f, 0f, w.toFloat(), h.toFloat())
        state.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = well.height() / 2f
        Materials.paint(
            canvas, well, radius, Material.INSET, tokens, state,
            MaterialOptions(specular = false, bevel = false, shadow = 1f),
            density,
        )

        if (isFocused) {
            Skeuo.drawGlow(canvas, well.centerX(), well.centerY(), well.height() * 1.1f, tokens.accent, 0.35f)
            Skeuo.drawHairline(canvas, well, radius, Tint.alphaFraction(tokens.accent, 0.85f), 1.4f, density)
        } else {
            Skeuo.drawHairline(canvas, well, radius, Tint.alphaFraction(tokens.chromeHi, 0.10f), 1f, density)
        }

        val iconSize = Skeuo.dp(context, 18f)
        val iconTop = well.centerY() - iconSize / 2f
        iconBounds.set(
            Skeuo.dp(context, 14f).toInt(), iconTop.toInt(),
            (Skeuo.dp(context, 14f) + iconSize).toInt(), (iconTop + iconSize).toInt(),
        )
        searchIcon?.let {
            it.bounds = iconBounds
            it.setTint(if (isFocused) tokens.accent else tokens.textFaint)
            it.draw(canvas)
        }

        if (!text.isNullOrEmpty()) {
            val clearSize = Skeuo.dp(context, 16f)
            val clearTop = well.centerY() - clearSize / 2f
            clearBounds.set(
                (well.right - Skeuo.dp(context, 14f) - clearSize).toInt(), clearTop.toInt(),
                (well.right - Skeuo.dp(context, 14f)).toInt(), (clearTop + clearSize).toInt(),
            )
            iconPaint.reset()
            iconPaint.isAntiAlias = true
            iconPaint.color = Tint.alphaFraction(tokens.textFaint, 0.28f)
            canvas.drawCircle(clearBounds.centerX().toFloat(), clearBounds.centerY().toFloat(), clearSize * 0.62f, iconPaint)
            clearIcon?.let {
                it.bounds = clearBounds
                it.setTint(tokens.textSecondary)
                it.draw(canvas)
            }
        } else {
            clearBounds.setEmpty()
        }

        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!clearBounds.isEmpty &&
            event.actionMasked == android.view.MotionEvent.ACTION_UP &&
            clearBounds.contains(event.x.toInt(), event.y.toInt())
        ) {
            setText("")
            onClear?.invoke()
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        state.invalidate()
        setHintTextColor(tokens.textFaint)
        setTextColor(tokens.textPrimary)
        invalidate()
    }
}

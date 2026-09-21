package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.MaterialState
import com.dynablox.launcher.design.Materials
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens

/** A container that paints one of the five physical finishes behind its children. */
open class SkeuoPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), SkeuoComponent {

    var material: Material = Material.CERAMIC
        set(value) {
            if (field != value) {
                field = value
                state.invalidate()
                invalidate()
            }
        }

    var cornerRadius: Float = Dimens.dp(context, 20f)
        set(value) {
            field = value
            state.invalidate()
            invalidateOutline()
            invalidate()
        }

    var options: MaterialOptions = MaterialOptions()
        set(value) {
            field = value
            state.invalidate()
            invalidate()
        }

    private val state = MaterialState()
    private var tokens: ThemeTokens = SkeuoTheme.tokens(context)
    private val body = RectF()

    init {
        setWillNotDraw(false)
        val a = context.obtainStyledAttributes(attrs, R.styleable.SkeuoPanel)
        material = when (a.getInt(R.styleable.SkeuoPanel_dbxMaterial, 0)) {
            1 -> Material.METAL
            2 -> Material.GLASS
            3 -> Material.RUBBER
            else -> Material.CERAMIC
        }
        cornerRadius = a.getDimension(
            R.styleable.SkeuoPanel_dbxCornerRadius,
            Dimens.dp(context, 20f),
        )
        val insetLook = a.getBoolean(R.styleable.SkeuoPanel_dbxInset, false)
        if (insetLook) material = Material.INSET
        a.recycle()

        elevation = Dimens.dp(context, 6f) * SkeuoTheme.shadowIntensity
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    cornerRadius.coerceAtMost(minOf(view.width, view.height) / 2f),
                )
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        body.set(0f, 0f, w.toFloat(), h.toFloat())
        state.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = cornerRadius.coerceAtMost(minOf(width, height) / 2f)
        Materials.paint(
            canvas = canvas,
            box = body,
            radius = r,
            material = material,
            tokens = tokens,
            state = state,
            options = options,
            density = resources.displayMetrics.density,
        )
        super.onDraw(canvas)
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        state.invalidate()
        elevation = Dimens.dp(context, 6f) * SkeuoTheme.shadowIntensity
        invalidateOutline()
        invalidate()
        children().forEach { (it as? SkeuoComponent)?.refreshTheme() }
    }
}

/** Marker for components that can re-tint themselves when the runtime theme changes. */
interface SkeuoComponent {
    fun refreshTheme()
}

/** Recursively refreshes a whole (overlay) hierarchy after a theme or scale change. */
fun View.refreshSkeuoTree() {
    (this as? SkeuoComponent)?.refreshTheme()
    if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).refreshSkeuoTree()
    }
}

private fun View.children(): List<View> = if (this is android.view.ViewGroup) {
    (0 until childCount).map { getChildAt(it) }
} else {
    emptyList()
}

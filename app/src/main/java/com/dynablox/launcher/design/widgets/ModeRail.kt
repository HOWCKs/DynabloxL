package com.dynablox.launcher.design.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.dynablox.launcher.core.modes.GameMode
import com.dynablox.launcher.core.modes.GameModes
import com.dynablox.launcher.core.modes.ModeTint
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Forged
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.Tint

/**
 * The mode selector: a horizontal rail of chevron keys, one per performance mode.
 *
 * Design decisions, each with a reason:
 *
 *  - **A rail, not a dropdown.** There are five modes and switching them is the primary action of
 *    a booster. Anything hidden behind a menu would be one tap too far.
 *  - **Chevron keys that interlock.** They read as one mechanism with a single active position —
 *    the shape itself says "these are mutually exclusive", which matches the engine's exclusivity.
 *  - **Icon + label, never colour alone.** ECO/TURBO/BATTLE have distinct tints, but colour-blind
 *    users and glare-lit screens get the word and the glyph too.
 *  - **The engaged key is the only lit one.** The busy key pulses instead, so "applying" and
 *    "applied" are never confused.
 *  - **Keys needing Shizuku are drawn dimmed but remain tappable** — tapping surfaces the
 *    permission prompt. Making them untappable would leave the user with no path forward.
 */
class ModeRail @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), SkeuoComponent {

    var modes: List<GameMode> = GameModes.SELECTABLE
        set(value) {
            field = value
            icons.clear()
            requestLayout()
            invalidate()
        }

    /** Id of the engaged mode; "off" (or anything unknown) means nothing is lit. */
    var activeId: String = GameModes.OFF.id
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Id of the mode currently being applied, if any. Drawn pulsing, not lit. */
    var busyId: String? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null) postInvalidateOnAnimation()
                invalidate()
            }
        }

    /** True when Shizuku is unavailable: privileged modes get the dimmed treatment. */
    var privilegedAvailable: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onModeSelected: ((GameMode) -> Unit)? = null

    private var tokens: ThemeTokens = SkeuoTheme.tokens(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val path = Path()
    private val cell = RectF()
    private val icons = HashMap<String, Drawable?>()
    private var pressedIndex = -1

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desired = Dimens.dp(context, 62f).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val count = modes.size
        if (count == 0 || width <= 0 || height <= 0) return

        val gap = Dimens.dp(context, 3f)
        val slot = (width - gap * (count - 1)) / count
        val density = resources.displayMetrics.density
        val pulse = busyPulse()

        for ((index, mode) in modes.withIndex()) {
            val left = index * (slot + gap)
            cell.set(left, 0f, left + slot, height.toFloat())

            val engaged = mode.id == activeId
            val busy = mode.id == busyId
            val blocked = mode.needsShizuku && !privilegedAvailable
            val accent = colorFor(mode.tint)

            val cut = Forged.chamfer(cell.width(), cell.height(), Dimens.dp(context, 10f))
            Forged.chamferPath(path, cell, cut)

            // Body: engaged keys are filled with a deep wash of their own tint so the rail reads
            // at a glance; the rest stay chassis-dark.
            fill.reset()
            fill.isAntiAlias = true
            fill.color = when {
                engaged -> Tint.mix(tokens.surfaceInset, accent, 0.30f)
                pressedIndex == index -> Tint.lighten(tokens.surfaceRaised, 0.06f)
                else -> tokens.surfaceRaised
            }
            canvas.drawPath(path, fill)

            // Rim: the state signal. Lit for engaged, pulsing for busy, structural otherwise.
            stroke.strokeWidth = if (engaged) 2f * density else 1f * density
            stroke.color = when {
                engaged -> accent
                busy -> Tint.alphaFraction(accent, pulse)
                blocked -> Tint.alphaFraction(tokens.chromeLo, 0.4f)
                else -> Tint.alphaFraction(tokens.chromeHi, 0.12f)
            }
            canvas.drawPath(path, stroke)

            val contentAlpha = if (blocked && !engaged) 0.45f else 1f

            // Glyph
            val icon = icons.getOrPut(mode.id) {
                runCatching { ContextCompat.getDrawable(context, mode.iconRes) }.getOrNull()
            }
            val iconSize = Dimens.dp(context, 20f)
            val textSize = Dimens.sp(context, 10f)
            val block = iconSize + textSize * 1.65f
            var cursor = cell.centerY() - block / 2f
            if (icon != null) {
                val l = (cell.centerX() - iconSize / 2f).toInt()
                val t = cursor.toInt()
                icon.setBounds(l, t, (l + iconSize).toInt(), (t + iconSize).toInt())
                val glyph = if (engaged) accent else tokens.textSecondary
                icon.colorFilter = PorterDuffColorFilter(
                    Tint.alphaFraction(glyph, contentAlpha),
                    PorterDuff.Mode.SRC_IN,
                )
                icon.draw(canvas)
            }
            cursor += iconSize + textSize * 0.35f

            label.textSize = textSize
            label.color = Tint.alphaFraction(
                if (engaged) tokens.textPrimary else tokens.textSecondary,
                contentAlpha,
            )
            canvas.drawText(
                context.getString(mode.nameRes).uppercase(),
                cell.centerX(),
                cursor - label.ascent(),
                label,
            )
        }

        // Only the busy key animates, and only when the user allows motion; everything else is
        // static, so an idle rail costs zero frames.
        if (busyId != null && Motion.ambientAllowed()) postInvalidateOnAnimation()
    }

    /**
     * Breathing alpha for the key being applied. Under "reduce motion" it settles at a constant
     * mid value, so the state is still distinguishable without anything moving.
     */
    private fun busyPulse(): Float {
        if (busyId == null) return 1f
        if (!Motion.ambientAllowed()) return 0.7f
        val phase = (android.os.SystemClock.uptimeMillis() % PULSE_MS).toFloat() / PULSE_MS
        return 0.45f + 0.55f * (0.5f - 0.5f * kotlin.math.cos(phase * 2f * Math.PI.toFloat()))
    }

    private fun colorFor(tint: ModeTint): Int = when (tint) {
        ModeTint.NEUTRAL -> tokens.textSecondary
        ModeTint.ACCENT -> tokens.accent
        ModeTint.SUCCESS -> tokens.success
        ModeTint.WARNING -> tokens.warning
        ModeTint.DANGER -> tokens.danger
    }

    private fun indexAt(x: Float): Int {
        val count = modes.size
        if (count == 0 || width <= 0) return -1
        val gap = Dimens.dp(context, 3f)
        val slot = (width - gap * (count - 1)) / count
        if (slot <= 0f) return -1
        return ((x / (slot + gap)).toInt()).coerceIn(0, count - 1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = indexAt(event.x)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val i = indexAt(event.x)
                if (i != pressedIndex) {
                    pressedIndex = i
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val i = pressedIndex
                pressedIndex = -1
                invalidate()
                if (i in modes.indices) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onModeSelected?.invoke(modes[i])
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun refreshTheme() {
        tokens = SkeuoTheme.tokens(context)
        icons.clear()
        invalidate()
    }

    private companion object {
        const val PULSE_MS = 1400L
    }
}

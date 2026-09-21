package com.dynablox.launcher.design

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.dynablox.launcher.R

/**
 * Resolved palette of the active theme. Custom views resolve this once (in `init` /
 * `onThemeChanged`) and cache it — never resolve attributes inside `onDraw`.
 */
class ThemeTokens private constructor(
    @ColorInt val surfaceBase: Int,
    @ColorInt val surfaceRaised: Int,
    @ColorInt val surfaceInset: Int,
    @ColorInt val surfaceGlass: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
    @ColorInt val textFaint: Int,
    @ColorInt val accent: Int,
    @ColorInt val accentDeep: Int,
    @ColorInt val success: Int,
    @ColorInt val warning: Int,
    @ColorInt val danger: Int,
    @ColorInt val metalLight: Int,
    @ColorInt val metalMid: Int,
    @ColorInt val metalDark: Int,
    @ColorInt val chromeHi: Int,
    @ColorInt val chromeLo: Int,
    @ColorInt val divider: Int,
    @ColorInt val ledOff: Int,
    @ColorInt val iconTint: Int,
    @ColorInt val specular: Int,
    @ColorInt val shadow: Int,
) {
    /** True when the resolved surface is dark (drives text/label inversion). */
    val isDark: Boolean = luminance(surfaceBase) < 0.45f

    companion object {

        fun from(context: Context): ThemeTokens {
            val value = TypedValue()
            fun color(@AttrRes attr: Int, @ColorInt fallback: Int): Int =
                if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback

            return ThemeTokens(
                surfaceBase = color(R.attr.dbxSurfaceBase, Color.parseColor("#0E1215")),
                surfaceRaised = color(R.attr.dbxSurfaceRaised, Color.parseColor("#151A1E")),
                surfaceInset = color(R.attr.dbxSurfaceInset, Color.parseColor("#0A0E10")),
                surfaceGlass = color(R.attr.dbxSurfaceGlass, Color.parseColor("#CC11171A")),
                textPrimary = color(R.attr.dbxTextPrimary, Color.parseColor("#EAF2F6")),
                textSecondary = color(R.attr.dbxTextSecondary, Color.parseColor("#B4BFC6")),
                textFaint = color(R.attr.dbxTextFaint, Color.parseColor("#7C8A93")),
                accent = color(R.attr.dbxAccent, Color.parseColor("#34A9FF")),
                accentDeep = color(R.attr.dbxAccentDeep, Color.parseColor("#1C6280")),
                success = color(R.attr.dbxSuccess, Color.parseColor("#3CC794")),
                warning = color(R.attr.dbxWarning, Color.parseColor("#E9A63C")),
                danger = color(R.attr.dbxDanger, Color.parseColor("#D24A3C")),
                metalLight = color(R.attr.dbxMetalLight, Color.parseColor("#4A555C")),
                metalMid = color(R.attr.dbxMetalMid, Color.parseColor("#232A2F")),
                metalDark = color(R.attr.dbxMetalDark, Color.parseColor("#151A1E")),
                chromeHi = color(R.attr.dbxChromeHi, Color.parseColor("#F4F8FA")),
                chromeLo = color(R.attr.dbxChromeLo, Color.parseColor("#5D6A72")),
                divider = color(R.attr.dbxDivider, Color.parseColor("#2A3238")),
                ledOff = color(R.attr.dbxLedOff, Color.parseColor("#333C42")),
                iconTint = color(R.attr.dbxIconTint, Color.parseColor("#B4BFC6")),
                specular = color(R.attr.dbxSpecular, Color.parseColor("#33FFFFFF")),
                shadow = color(R.attr.dbxShadow, Color.parseColor("#99000000")),
            )
        }

        /** Perceptual luminance, 0..1 (Rec. 709 coefficients). */
        fun luminance(@ColorInt color: Int): Float {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
}

/** Colour helpers used across the material renderers. */
object Tint {

    fun alpha(@ColorInt color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun alphaFraction(@ColorInt color: Int, fraction: Float): Int =
        alpha(color, (fraction.coerceIn(0f, 1f) * 255f).toInt())

    fun lighten(@ColorInt color: Int, fraction: Float): Int = mix(color, Color.WHITE, fraction)

    fun darken(@ColorInt color: Int, fraction: Float): Int = mix(color, Color.BLACK, fraction)

    fun mix(@ColorInt from: Int, @ColorInt to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt()
        return Color.argb(a, r, g, b)
    }

    /** Picks readable text for an arbitrary background. */
    fun onColor(@ColorInt background: Int, @ColorInt light: Int, @ColorInt dark: Int): Int =
        if (ThemeTokens.luminance(background) > 0.55f) dark else light
}

package com.dynablox.launcher.fx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.StringRes
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.design.Textures

/**
 * A screen-wide look, applied as a translucent overlay above every app.
 *
 * Honest scope: without screen capture an overlay can only *add* light — vignette, tint, grain and
 * scanlines — it cannot re-shade the pixels underneath. That is exactly what these presets do, and
 * it is enough for the useful cases (eye comfort, cinematic framing, CRT flavour).
 */
data class FxPreset(
    val id: String,
    @StringRes val nameRes: Int,
    val vignette: Float,
    val tintColor: Int,
    val tintAlpha: Float,
    val grain: Float,
    val scanlines: Float,
)

object FxPresets {
    val ALL: List<FxPreset> = listOf(
        FxPreset("none", R.string.fx_preset_none, 0f, Color.TRANSPARENT, 0f, 0f, 0f),
        FxPreset("cinema", R.string.fx_preset_cinema, 0.78f, Color.parseColor("#0A0D12"), 0.10f, 0.05f, 0f),
        FxPreset("noir", R.string.fx_preset_noir, 0.62f, Color.parseColor("#1B2A33"), 0.20f, 0.20f, 0f),
        FxPreset("crt", R.string.fx_preset_crt, 0.52f, Color.parseColor("#08131A"), 0.12f, 0.08f, 0.34f),
        FxPreset("eyeComfort", R.string.fx_preset_eye, 0.16f, Color.parseColor("#E9A63C"), 0.18f, 0f, 0f),
        FxPreset("petrol", R.string.fx_preset_petrol, 0.58f, Color.parseColor("#143A4C"), 0.16f, 0.04f, 0f),
    )

    fun byId(id: String): FxPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/** Full-screen, touch-transparent view that paints the selected [FxPreset]. */
class FxView(context: Context, private val settings: AppSettings) : android.view.View(context) {

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint()
    private val scanPaint = Paint()
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var vignetteShader: Shader? = null
    private var scanShader: Shader? = null
    private var grainShader: Shader? = null
    private var lastW = -1
    private var lastH = -1

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastW = w
        lastH = h
        vignetteShader = null
    }

    override fun onDraw(canvas: Canvas) {
        val preset = FxPresets.byId(settings.fxPreset)
        if (preset.id == "none") return
        val amount = settings.fxAmount.coerceIn(0f, 1f)
        val opacity = settings.fxOpacity.coerceIn(0f, 1f)
        if (amount <= 0.01f || opacity <= 0.01f) return

        val w = width.toFloat()
        val h = height.toFloat()

        // tint
        if (preset.tintAlpha > 0f) {
            tintPaint.color = preset.tintColor
            tintPaint.alpha = (preset.tintAlpha * amount * opacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, tintPaint)
        }

        // scanlines
        if (preset.scanlines > 0f) {
            val shader = scanShader ?: buildScanShader().also { scanShader = it }
            scanPaint.shader = shader
            scanPaint.alpha = (preset.scanlines * amount * opacity * settings.fxAmount * 255f)
                .toInt().coerceIn(0, 140)
            canvas.drawRect(0f, 0f, w, h, scanPaint)
        }

        // grain
        val grainAmount = (preset.grain + settings.fxGrain) * amount * opacity
        if (grainAmount > 0.01f) {
            if (grainShader == null) {
                grainShader = Textures.shader(Textures.noise(size = 128, contrast = 26), 255).shader
            }
            grainPaint.shader = grainShader
            grainPaint.alpha = (grainAmount * 190f).toInt().coerceIn(0, 120)
            canvas.drawRect(0f, 0f, w, h, grainPaint)
        }

        // vignette
        if (preset.vignette > 0f) {
            if (vignetteShader == null || lastW != width || lastH != height) {
                vignetteShader = RadialGradient(
                    w / 2f, h / 2f, maxOf(w, h) * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(255, 0, 0, 0)),
                    floatArrayOf(0f, 0.42f, 1f),
                    Shader.TileMode.CLAMP,
                )
                lastW = width
                lastH = height
            }
            vignettePaint.shader = vignetteShader
            vignettePaint.alpha = (preset.vignette * amount * opacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, vignettePaint)
        }
    }

    private fun buildScanShader(): Shader {
        val size = 4
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val value = if (y % 4 == 0) 0xFF000000.toInt() else 0x00000000
            for (x in 0 until size) pixels[y * size + x] = value
        }
        val bitmap = android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        return android.graphics.BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}

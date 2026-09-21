package com.dynablox.launcher.design

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * The material system.
 *
 * Five physical finishes are available to every component. Each one is drawn procedurally from the
 * active [ThemeTokens]: body gradient, micro-texture, chamfered bevel, specular reflection and
 * inner shading. Nothing is a bitmap asset, so materials stay crisp at any density and can be
 * re-tinted instantly when the theme flips.
 */
enum class Material {
    /** Polished technical ceramic — default panel and keycap finish. */
    CERAMIC,

    /** Brushed anodised aluminium — decks, bezels, fader caps. */
    METAL,

    /** Smoked glass / acrylic — floating panels and the orb. */
    GLASS,

    /** Premium matte rubber — grips, tracks, recessed pads. */
    RUBBER,

    /** Recessed well — LCD windows, search fields, slider tracks. */
    INSET,
}

/** Per-view cache so materials cost nothing at 60 fps after the first layout pass. */
class MaterialState {
    var material: Material? = null
    var width: Int = -1
    var height: Int = -1
    var radius: Int = -1
    var baseToken: Int = 0
    var accentToken: Int = 0
    var bodyShader: Shader? = null
    var specularShader: Shader? = null
    var bevelShader: Shader? = null
    var texturePaint: Paint? = null

    fun isStale(
        target: Material,
        box: RectF,
        cornerRadius: Float,
        tokens: ThemeTokens,
    ): Boolean = material != target ||
        width != box.width().toInt() ||
        height != box.height().toInt() ||
        radius != cornerRadius.toInt() ||
        baseToken != tokens.surfaceBase ||
        accentToken != tokens.accent

    fun remember(target: Material, box: RectF, cornerRadius: Float, tokens: ThemeTokens) {
        material = target
        width = box.width().toInt()
        height = box.height().toInt()
        radius = cornerRadius.toInt()
        baseToken = tokens.surfaceBase
        accentToken = tokens.accent
    }

    fun invalidate() {
        width = -1
        height = -1
        material = null
        bodyShader = null
        specularShader = null
        bevelShader = null
        texturePaint = null
    }
}

data class MaterialOptions(
    /** 0 = flat, 1 = full studio shading. */
    val shadow: Float = 1f,
    val bevel: Boolean = true,
    val texture: Boolean = true,
    val specular: Boolean = true,
    val innerShade: Boolean = true,
    /** Extra tint blended into the body (accent-lit surfaces). */
    val tint: Int? = null,
    val tintAmount: Float = 0.16f,
    val opacity: Float = 1f,
)

object Materials {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)

    fun paint(
        canvas: Canvas,
        box: RectF,
        radius: Float,
        material: Material,
        tokens: ThemeTokens,
        state: MaterialState,
        options: MaterialOptions = MaterialOptions(),
        density: Float = 1f,
    ) {
        if (box.width() <= 0f || box.height() <= 0f) return

        if (state.isStale(material, box, radius, tokens)) {
            state.invalidate()
            state.remember(material, box, radius, tokens)
            buildShaders(state, material, box, radius, tokens, options)
        }

        val alpha = (options.opacity.coerceIn(0f, 1f) * 255f).toInt()

        // 1 — body
        fill.reset()
        fill.isAntiAlias = true
        fill.shader = state.bodyShader
        fill.alpha = alpha
        canvas.drawRoundRect(box, radius, radius, fill)

        // 2 — micro texture (clipped to the body)
        val texture = state.texturePaint
        if (texture != null && options.texture) {
            val save = canvas.save()
            Skeuo.roundRectPath(SkeuoPathHolder.path, box, radius)
            canvas.clipPath(SkeuoPathHolder.path)
            overlay.reset()
            overlay.isAntiAlias = false
            overlay.shader = texture.shader
            overlay.alpha = (texture.alpha * alpha / 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(box, overlay)
            canvas.restoreToCount(save)
        }

        // 3 — inner shading / recess
        if (options.innerShade) {
            val depth = when (material) {
                Material.INSET -> 5f
                Material.RUBBER -> 4f
                Material.GLASS -> 3f
                else -> 2.5f
            } * options.shadow
            Skeuo.drawInnerShade(
                canvas, box, radius,
                if (material == Material.INSET) tokens.shadow else darkenFor(tokens, material),
                depth, density,
            )
        }

        // 4 — specular reflection
        if (options.specular) {
            val specularColor = when (material) {
                Material.GLASS -> Tint.alphaFraction(tokens.chromeHi, 0.22f)
                Material.CERAMIC -> Tint.alphaFraction(tokens.chromeHi, 0.14f)
                Material.METAL -> Tint.alphaFraction(tokens.chromeHi, 0.10f)
                Material.RUBBER -> Tint.alphaFraction(tokens.chromeHi, 0.05f)
                Material.INSET -> Tint.alphaFraction(tokens.chromeHi, 0.04f)
            }
            state.specularShader?.let {
                fill.reset()
                fill.isAntiAlias = true
                fill.shader = it
                fill.alpha = (fill.alpha.coerceAtLeast(0) * alpha / 255f).toInt()
                val save = canvas.save()
                Skeuo.roundRectPath(SkeuoPathHolder.path, box, radius)
                canvas.clipPath(SkeuoPathHolder.path)
                canvas.drawRect(box, fill)
                canvas.restoreToCount(save)
            } ?: run {
                Skeuo.drawSpecular(canvas, box, radius, specularColor, 0.42f)
            }
        }

        // 5 — chamfer / hairline
        if (options.bevel) {
            val hi = Tint.alphaFraction(tokens.chromeHi, if (material == Material.METAL) 0.42f else 0.24f)
            val lo = Tint.alphaFraction(tokens.shadow, if (material == Material.INSET) 0.18f else 0.62f)
            Skeuo.drawBevel(canvas, box, radius, hi, lo, 1.1f, density)
        } else {
            edge.reset()
            edge.isAntiAlias = true
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = 1f * density
            edge.color = Tint.alphaFraction(tokens.divider, 0.8f)
            Skeuo.inset(box, edge.strokeWidth / 2f, SkeuoPathHolder.rect)
            canvas.drawRoundRect(
                SkeuoPathHolder.rect,
                (radius - edge.strokeWidth / 2f).coerceAtLeast(0f),
                (radius - edge.strokeWidth / 2f).coerceAtLeast(0f),
                edge,
            )
        }
    }

    private fun buildShaders(
        state: MaterialState,
        material: Material,
        box: RectF,
        radius: Float,
        tokens: ThemeTokens,
        options: MaterialOptions,
    ) {
        val top: Int
        val bottom: Int
        var mid: Int? = null

        when (material) {
            Material.CERAMIC -> {
                top = Tint.lighten(tokens.surfaceRaised, 0.10f)
                bottom = Tint.darken(tokens.surfaceRaised, 0.16f)
                state.texturePaint = if (options.texture) {
                    Textures.shader(Textures.noise(), 12)
                } else null
            }

            Material.METAL -> {
                top = Tint.lighten(tokens.metalLight, 0.12f)
                mid = tokens.metalMid
                bottom = Tint.darken(tokens.metalDark, 0.10f)
                state.texturePaint = if (options.texture) {
                    Textures.shader(Textures.brushed(), 34)
                } else null
            }

            Material.GLASS -> {
                val base = tokens.surfaceGlass
                top = Tint.alphaFraction(Tint.lighten(base, 0.18f), 0.94f)
                bottom = Tint.alphaFraction(Tint.darken(base, 0.10f), 0.88f)
                state.texturePaint = if (options.texture) {
                    Textures.shader(Textures.noise(size = 64, contrast = 14), 8)
                } else null
            }

            Material.RUBBER -> {
                top = Tint.lighten(tokens.metalDark, 0.06f)
                bottom = Tint.darken(tokens.metalDark, 0.34f)
                state.texturePaint = if (options.texture) {
                    Textures.shader(Textures.microDot(), 30)
                } else null
            }

            Material.INSET -> {
                top = Tint.darken(tokens.surfaceInset, 0.18f)
                bottom = Tint.lighten(tokens.surfaceInset, 0.06f)
                state.texturePaint = null
            }
        }

        val tintedTop = options.tint?.let { Tint.mix(top, it, options.tintAmount) } ?: top
        val tintedBottom = options.tint?.let { Tint.mix(bottom, it, options.tintAmount) } ?: bottom

        state.bodyShader = if (mid != null) {
            val tintedMid = options.tint?.let { Tint.mix(mid, it, options.tintAmount) } ?: mid
            android.graphics.LinearGradient(
                box.left, box.top, box.left * 0.98f + box.right * 0.02f, box.bottom,
                intArrayOf(tintedTop, tintedMid, tintedBottom),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            Skeuo.faceGradient(box, tintedTop, tintedBottom)
        }

        state.specularShader = if (options.specular) {
            val glossBottom = box.top + box.height() * 0.44f
            val gloss = when (material) {
                Material.GLASS -> Tint.alphaFraction(tokens.chromeHi, 0.20f)
                Material.CERAMIC -> Tint.alphaFraction(tokens.chromeHi, 0.13f)
                Material.METAL -> Tint.alphaFraction(tokens.chromeHi, 0.09f)
                Material.RUBBER -> Tint.alphaFraction(tokens.chromeHi, 0.045f)
                Material.INSET -> Tint.alphaFraction(tokens.chromeHi, 0.035f)
            }
            android.graphics.LinearGradient(
                box.left, box.top, box.left, glossBottom,
                intArrayOf(gloss, Tint.alpha(gloss, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else null

        state.bevelShader = if (options.bevel) {
            Skeuo.bevelGradient(
                box,
                Tint.alphaFraction(tokens.chromeHi, if (material == Material.METAL) 0.44f else 0.26f),
                Tint.alphaFraction(tokens.shadow, 0.62f),
            )
        } else null
    }

    private fun darkenFor(tokens: ThemeTokens, material: Material): Int = when (material) {
        Material.GLASS, Material.INSET, Material.RUBBER -> tokens.shadow
        else -> Tint.alphaFraction(tokens.shadow, 0.7f)
    }
}

/** Scratch objects shared by the material painter (main thread only). */
internal object SkeuoPathHolder {
    val path = android.graphics.Path()
    val rect = RectF()
}

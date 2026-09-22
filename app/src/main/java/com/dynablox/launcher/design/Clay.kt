package com.dynablox.launcher.design

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * CLAY MORPHIST — the second material vocabulary of the product.
 *
 * Where [Skeuo] describes a machined instrument (brushed metal, chrome chamfers, a hard specular
 * hotspot), this describes *matter that was pressed into shape*. The two are mutually exclusive
 * finishes over the same geometry, so a surface can migrate from one to the other without any
 * layout, hierarchy or component change.
 *
 * The physics that define the finish — and the reason each one exists:
 *
 *  - **Diffuse, never glossy.** Unfired clay scatters light instead of mirroring it. There is no
 *    specular streak; the highlight is a broad, low-contrast bloom on the light-facing shoulder.
 *    This is what keeps the surface from reading as plastic.
 *  - **Thick shoulders, not 1 px bevels.** The edge is a wide gradient band whose width scales with
 *    the corner radius, so the form looks pressed rather than cut. A hairline would betray the
 *    material instantly.
 *  - **Paired shadows.** A soft ambient shadow below and a faint counter-light above. Neumorphism
 *    uses two symmetric shadows and loses all hierarchy; here the dark side is roughly three times
 *    the weight of the light side, so depth still reads as depth.
 *  - **Subsurface warmth.** A barely-visible radial core, warmer than the body, mimics light
 *    penetrating a few millimetres into the material. It is what makes the surface feel soft.
 *  - **Contact darkening.** Where a form is recessed the shadow gathers in the corners rather than
 *    tracing the outline, because real matter compresses unevenly.
 *
 * Everything is painted with gradients and one cached grain bitmap — no blur, no layer
 * `saveLayer`, no bitmap assets — so it costs the same as the metal finish at 60 fps.
 */
object Clay {

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shoulder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scratch = RectF()

    /**
     * Paints a clay surface. Signature mirrors [Materials.paint] so the router can forward to it
     * without any call site knowing which finish is active.
     */
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

        val recessed = material == Material.INSET
        val alpha = (options.opacity.coerceIn(0f, 1f) * 255f).toInt()
        // Clay corners are fuller than machined ones: the press cannot hold a tight radius.
        val r = clayRadius(box, radius)

        if (state.isStale(material, box, radius, tokens)) {
            state.invalidate()
            state.remember(material, box, radius, tokens)
            buildShaders(state, material, box, r, tokens, options, recessed)
        }

        // 1 — ambient bed. Raised forms sit on a soft shadow; recessed ones skip it entirely,
        //     because a dent casts nothing.
        if (!recessed && options.shadow > 0.01f) {
            drawAmbient(canvas, box, r, tokens, options.shadow, density)
        }

        // 2 — body
        body.reset()
        body.isAntiAlias = true
        body.shader = state.bodyShader
        body.alpha = alpha
        canvas.drawRoundRect(box, r, r, body)

        // 3 — subsurface core: the warmth that separates clay from painted plastic.
        state.specularShader?.let { core ->
            val save = canvas.save()
            Skeuo.roundRectPath(SkeuoPathHolder.path, box, r)
            canvas.clipPath(SkeuoPathHolder.path)
            overlay.reset()
            overlay.isAntiAlias = true
            overlay.shader = core
            overlay.alpha = alpha
            canvas.drawRect(box, overlay)
            canvas.restoreToCount(save)
        }

        // 4 — matte grain, so large surfaces do not band on cheap panels.
        val grain = state.texturePaint
        if (grain != null && options.texture) {
            val save = canvas.save()
            Skeuo.roundRectPath(SkeuoPathHolder.path, box, r)
            canvas.clipPath(SkeuoPathHolder.path)
            overlay.reset()
            overlay.isAntiAlias = false
            overlay.shader = grain.shader
            overlay.alpha = (grain.alpha * alpha / 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(box, overlay)
            canvas.restoreToCount(save)
        }

        // 5 — pressed shoulders
        drawShoulders(canvas, box, r, tokens, density, recessed, options)

        // 6 — contact darkening inside recesses (wells, tracks, LCD windows)
        if (recessed) {
            drawRecessGather(canvas, box, r, tokens, density)
        }
    }

    /**
     * Clay cannot hold a sharp corner: the radius is pushed towards a fuller curve, clamped so a
     * wide, short surface never turns into a capsule by accident.
     */
    fun clayRadius(box: RectF, radius: Float): Float =
        clayRadius(box.width(), box.height(), radius)

    /**
     * Pure form of [clayRadius]. Kept free of Canvas/RectF so the geometry rule is unit-testable on
     * the JVM, where the android.jar stubs throw.
     */
    fun clayRadius(width: Float, height: Float, radius: Float): Float {
        // A view measured at 0x0 (or an inverted rect) must not yield a negative radius: Canvas
        // would throw, and it happens on the first layout pass of every overlay.
        val limit = (min(width, height) / 2f).coerceAtLeast(0f)
        return (radius * 1.34f + 2f).coerceAtMost(limit).coerceAtLeast(0f)
    }

    /**
     * Soft bed under a raised form: two stacked passes instead of a blur. The lower pass is wide
     * and faint (ambient occlusion), the upper one tight and darker (contact).
     */
    private fun drawAmbient(
        canvas: Canvas,
        box: RectF,
        radius: Float,
        tokens: ThemeTokens,
        intensity: Float,
        density: Float,
    ) {
        val strength = intensity.coerceIn(0f, 1f)
        overlay.reset()
        overlay.isAntiAlias = true

        val spread = 7f * density * strength
        val steps = 4
        for (i in steps downTo 1) {
            val f = i / steps.toFloat()
            scratch.set(
                box.left - spread * f * 0.35f,
                box.top - spread * f * 0.10f + spread * f * 0.55f,
                box.right + spread * f * 0.35f,
                box.bottom + spread * f * 0.80f,
            )
            overlay.color = Tint.alphaFraction(tokens.shadow, 0.055f * strength)
            canvas.drawRoundRect(scratch, radius + spread * f * 0.5f, radius + spread * f * 0.5f, overlay)
        }
    }

    /**
     * The defining gesture of the finish: a wide light shoulder on the top-left and a wide shade on
     * the bottom-right, both drawn as thick arcs of the rounded rect rather than a stroke, so the
     * edge looks pressed. On a recess the two swap, which is what makes a well read as a well.
     */
    private fun drawShoulders(
        canvas: Canvas,
        box: RectF,
        radius: Float,
        tokens: ThemeTokens,
        density: Float,
        recessed: Boolean,
        options: MaterialOptions,
    ) {
        if (!options.bevel) return
        // Shoulder width follows the radius: a fuller corner implies a thicker rim of material.
        val width = (radius * 0.24f).coerceIn(2.2f * density, 7f * density)
        val save = canvas.save()
        Skeuo.roundRectPath(SkeuoPathHolder.path, box, radius)
        canvas.clipPath(SkeuoPathHolder.path)

        val lightAlpha = if (recessed) 0.05f else 0.16f
        val shadeAlpha = if (recessed) 0.30f else 0.22f

        shoulder.reset()
        shoulder.isAntiAlias = true
        shoulder.style = Paint.Style.STROKE
        shoulder.strokeWidth = width * 2f

        // Light shoulder (top-left for a raised form, bottom-right for a recess).
        shoulder.shader = LinearGradient(
            box.left, box.top, box.left + box.width() * 0.5f, box.top + box.height() * 0.62f,
            intArrayOf(
                Tint.alphaFraction(tokens.chromeHi, if (recessed) 0f else lightAlpha),
                Tint.alpha(tokens.chromeHi, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(box, radius, radius, shoulder)

        // Shade shoulder, anchored to the opposite corner.
        shoulder.shader = LinearGradient(
            box.right, box.bottom, box.right - box.width() * 0.55f, box.bottom - box.height() * 0.68f,
            intArrayOf(
                Tint.alphaFraction(tokens.shadow, shadeAlpha),
                Tint.alpha(tokens.shadow, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(box, radius, radius, shoulder)

        shoulder.shader = null
        canvas.restoreToCount(save)
    }

    /** Inside a recess the shade gathers in the upper corners, where matter was pushed away. */
    private fun drawRecessGather(
        canvas: Canvas,
        box: RectF,
        radius: Float,
        tokens: ThemeTokens,
        density: Float,
    ) {
        val save = canvas.save()
        Skeuo.roundRectPath(SkeuoPathHolder.path, box, radius)
        canvas.clipPath(SkeuoPathHolder.path)
        overlay.reset()
        overlay.isAntiAlias = true
        overlay.shader = LinearGradient(
            box.left, box.top, box.left, box.top + (box.height() * 0.5f).coerceAtLeast(2f * density),
            intArrayOf(Tint.alphaFraction(tokens.shadow, 0.26f), Tint.alpha(tokens.shadow, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(box, overlay)
        overlay.shader = null
        canvas.restoreToCount(save)
    }

    /**
     * Rim shader for circular forms (the orb bezel, the gauge dial ring).
     *
     * The machined finish uses a chrome sweep — a mirror that rotates a hard highlight around the
     * circumference. Clay cannot mirror, so the rim is a soft directional band: bright where the
     * key light lands on the upper-left shoulder, shaded at the opposite side, with no wrap-around
     * hotspot. Returned as a plain linear gradient so it costs the same as the sweep it replaces.
     */
    fun rimShader(cx: Float, cy: Float, radius: Float, tokens: ThemeTokens): Shader = LinearGradient(
        cx - radius * 0.72f, cy - radius,
        cx + radius * 0.62f, cy + radius,
        intArrayOf(
            Tint.alphaFraction(tokens.chromeHi, 0.20f),
            Tint.alphaFraction(tokens.chromeHi, 0.05f),
            Tint.alphaFraction(tokens.shadow, 0.26f),
        ),
        floatArrayOf(0f, 0.48f, 1f),
        Shader.TileMode.CLAMP,
    )

    /**
     * Grip marking for thumbs and caps.
     *
     * Knurling is a machining operation: it cannot exist in pressed matter. The clay equivalent is
     * a thumb dimple — a shallow depression with light gathered on its lower lip, which reads as
     * "pressed by a finger" and communicates the same affordance ("this part is grabbable").
     */
    fun drawGripDimple(
        canvas: Canvas,
        box: RectF,
        tokens: ThemeTokens,
        density: Float,
    ) {
        val r = (min(box.width(), box.height()) * 0.26f).coerceAtLeast(2f * density)
        val cx = box.centerX()
        val cy = box.centerY()
        overlay.reset()
        overlay.isAntiAlias = true
        // Depression: darker at the top, where the surface curves away from the key light.
        overlay.shader = LinearGradient(
            cx, cy - r, cx, cy + r,
            intArrayOf(
                Tint.alphaFraction(tokens.shadow, 0.34f),
                Tint.alphaFraction(tokens.chromeHi, 0.13f),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, overlay)
        overlay.shader = null
    }

    private fun buildShaders(
        state: MaterialState,
        material: Material,
        box: RectF,
        radius: Float,
        tokens: ThemeTokens,
        options: MaterialOptions,
        recessed: Boolean,
    ) {
        // Clay bodies are nearly flat: a strong gradient would read as glass. The vertical
        // difference stays under ~10% so the form is described by its shoulders, not by a ramp.
        val base = when (material) {
            Material.INSET -> tokens.surfaceInset
            Material.GLASS -> tokens.surfaceGlass
            Material.RUBBER -> Tint.darken(tokens.surfaceRaised, 0.10f)
            Material.METAL -> Tint.mix(tokens.surfaceRaised, tokens.metalMid, 0.34f)
            Material.CERAMIC -> tokens.surfaceRaised
        }

        val top: Int
        val bottom: Int
        if (recessed) {
            top = Tint.darken(base, 0.10f)
            bottom = Tint.lighten(base, 0.04f)
        } else {
            top = Tint.lighten(base, 0.055f)
            bottom = Tint.darken(base, 0.075f)
        }

        val tintedTop = options.tint?.let { Tint.mix(top, it, options.tintAmount) } ?: top
        val tintedBottom = options.tint?.let { Tint.mix(bottom, it, options.tintAmount) } ?: bottom
        state.bodyShader = Skeuo.faceGradient(box, tintedTop, tintedBottom)

        // Subsurface core — reuses the specular slot, but it is a warm bloom, not a hotspot.
        // Recessed forms get none: light does not pool at the bottom of a dent.
        state.specularShader = if (options.specular && !recessed) {
            val warm = Tint.mix(tokens.chromeHi, options.tint ?: tokens.accent, 0.22f)
            RadialGradient(
                box.left + box.width() * 0.34f,
                box.top + box.height() * 0.28f,
                (min(box.width(), box.height()) * 0.92f).coerceAtLeast(1f),
                intArrayOf(Tint.alphaFraction(warm, 0.085f), Tint.alpha(warm, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            null
        }

        state.texturePaint = if (options.texture) {
            // Coarser and fainter than the metal grain: it should read as matte, not as noise.
            Textures.shader(Textures.matte(), if (material == Material.GLASS) 7 else 11)
        } else {
            null
        }

        state.bevelShader = null
    }
}

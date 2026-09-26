package com.dynablox.launcher.design

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * FORGED — the high-performance console finish.
 *
 * Where [Skeuo] is a machined instrument and [Clay] is pressed matter, this is a *gaming rig
 * chassis*: cut carbon panels with clipped corners, a hot rim light along the leading edges, and
 * hard contrast. It is the vocabulary of the reference console — angular, lit, unmistakably built
 * for performance.
 *
 * The rules that keep it from becoming the usual neon cliché:
 *
 *  - **The panel is dark and matte; only the EDGES are lit.** Glow is a rim phenomenon, not a fill.
 *    Filling shapes with neon is what makes "gamer" UIs unreadable.
 *  - **Corners are chamfered, not rounded.** A 45-degree cut reads as machined plate. The chamfer
 *    size is capped so small controls stay legible instead of turning into octagons.
 *  - **One light source, top-left, plus an accent rim.** The accent is the only saturated colour on
 *    the surface, so it always means "this is the active/energised element".
 *  - **Text never sits on a gradient.** The body stays flat behind content; the drama lives on the
 *    perimeter, which is why this finish can be aggressive and still readable.
 *
 * Costs the same as the other finishes: gradients plus one cached grain bitmap, no blur.
 */
object Forged {

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

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
        val cut = chamfer(box.width(), box.height(), radius)

        if (state.isStale(material, box, radius, tokens)) {
            state.invalidate()
            state.remember(material, box, radius, tokens)
            buildShaders(state, material, box, tokens, options, recessed)
        }

        chamferPath(path, box, cut)

        // 1 — plate
        body.reset()
        body.isAntiAlias = true
        body.shader = state.bodyShader
        body.alpha = alpha
        canvas.drawPath(path, body)

        // 2 — carbon grain, clipped to the plate
        val grain = state.texturePaint
        if (grain != null && options.texture) {
            val save = canvas.save()
            canvas.clipPath(path)
            overlay.reset()
            overlay.isAntiAlias = false
            overlay.shader = grain.shader
            overlay.alpha = (grain.alpha * alpha / 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(box, overlay)
            canvas.restoreToCount(save)
        }

        // 3 — top sheen: a single flat band, not a glossy dome.
        state.specularShader?.let { sheen ->
            val save = canvas.save()
            canvas.clipPath(path)
            overlay.reset()
            overlay.isAntiAlias = true
            overlay.shader = sheen
            overlay.alpha = alpha
            canvas.drawRect(box, overlay)
            canvas.restoreToCount(save)
        }

        // 4 — rim light. The signature of the finish, and the reason it reads as energised
        //     hardware rather than a flat dark card.
        if (options.bevel) {
            drawRim(canvas, box, cut, tokens, options, density, recessed)
        }

        // 5 — recessed wells darken from the top, like a cut into the plate.
        if (recessed) {
            val save = canvas.save()
            canvas.clipPath(path)
            overlay.reset()
            overlay.isAntiAlias = true
            overlay.shader = LinearGradient(
                box.left, box.top, box.left, box.top + (box.height() * 0.55f).coerceAtLeast(1f),
                intArrayOf(Tint.alphaFraction(tokens.shadow, 0.42f), Tint.alpha(tokens.shadow, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(box, overlay)
            overlay.shader = null
            canvas.restoreToCount(save)
        }
    }

    /**
     * Chamfer depth for a plate. Pure (no RectF) so the geometry rule is unit-testable on the JVM.
     *
     * Capped at a fraction of the shortest side: without the cap a small square control would lose
     * its whole face to the cut and read as a diamond.
     */
    fun chamfer(width: Float, height: Float, radius: Float): Float {
        val shortest = min(width, height)
        if (shortest <= 0f) return 0f
        return (radius * 0.85f + 1f).coerceAtMost(shortest * 0.30f).coerceAtLeast(0f)
    }

    fun chamfer(box: RectF, radius: Float): Float = chamfer(box.width(), box.height(), radius)

    /**
     * Octagonal plate outline: the four corners are cut at 45 degrees. Falls back to a plain
     * rectangle when the cut collapses, so a zero-sized view still produces a closed path.
     */
    fun chamferPath(target: Path, box: RectF, cut: Float): Path {
        target.reset()
        if (cut <= 0.5f) {
            target.addRect(box, Path.Direction.CW)
            return target
        }
        target.moveTo(box.left + cut, box.top)
        target.lineTo(box.right - cut, box.top)
        target.lineTo(box.right, box.top + cut)
        target.lineTo(box.right, box.bottom - cut)
        target.lineTo(box.right - cut, box.bottom)
        target.lineTo(box.left + cut, box.bottom)
        target.lineTo(box.left, box.bottom - cut)
        target.lineTo(box.left, box.top + cut)
        target.close()
        return target
    }

    /**
     * Perimeter light. Two strokes: a cool structural edge all the way around, then a hot accent
     * pass biased to the top-left leading edges so the plate has a direction.
     */
    private fun drawRim(
        canvas: Canvas,
        box: RectF,
        cut: Float,
        tokens: ThemeTokens,
        options: MaterialOptions,
        density: Float,
        recessed: Boolean,
    ) {
        val accent = options.tint ?: tokens.accent
        rim.reset()
        rim.isAntiAlias = true
        rim.style = Paint.Style.STROKE

        // Structural edge: defines the cut even when the accent is off-screen.
        rim.strokeWidth = 1f * density
        rim.color = Tint.alphaFraction(
            if (recessed) tokens.shadow else tokens.chromeHi,
            if (recessed) 0.5f else 0.16f,
        )
        rim.shader = null
        canvas.drawPath(path, rim)

        if (recessed) return

        // Accent pass: strongest at the top-left, gone by the bottom-right, so a wall of panels
        // does not turn into a uniform glowing grid.
        rim.strokeWidth = 1.6f * density
        rim.shader = LinearGradient(
            box.left, box.top, box.right * 0.72f + box.left * 0.28f, box.bottom,
            intArrayOf(
                Tint.alphaFraction(accent, 0.85f),
                Tint.alphaFraction(accent, 0.22f),
                Tint.alpha(accent, 0),
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, rim)
        rim.shader = null
    }

    private fun buildShaders(
        state: MaterialState,
        material: Material,
        box: RectF,
        tokens: ThemeTokens,
        options: MaterialOptions,
        recessed: Boolean,
    ) {
        val base = when (material) {
            Material.INSET -> tokens.surfaceInset
            Material.GLASS -> tokens.surfaceGlass
            Material.RUBBER -> Tint.darken(tokens.surfaceRaised, 0.22f)
            Material.METAL -> Tint.mix(tokens.surfaceRaised, tokens.metalMid, 0.5f)
            Material.CERAMIC -> tokens.surfaceRaised
        }

        // Plates are dark and nearly flat: contrast belongs on the rim, not under the text.
        val top = if (recessed) Tint.darken(base, 0.20f) else Tint.lighten(base, 0.07f)
        val bottom = if (recessed) base else Tint.darken(base, 0.22f)
        val tintedTop = options.tint?.let { Tint.mix(top, it, options.tintAmount * 0.5f) } ?: top
        val tintedBottom = options.tint?.let { Tint.mix(bottom, it, options.tintAmount * 0.5f) } ?: bottom
        state.bodyShader = Skeuo.faceGradient(box, tintedTop, tintedBottom)

        state.specularShader = if (options.specular && !recessed) {
            LinearGradient(
                box.left, box.top, box.left, box.top + (box.height() * 0.30f).coerceAtLeast(1f),
                intArrayOf(Tint.alphaFraction(tokens.chromeHi, 0.07f), Tint.alpha(tokens.chromeHi, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            null
        }

        state.texturePaint = if (options.texture) {
            Textures.shader(Textures.carbon(), if (material == Material.GLASS) 10 else 16)
        } else {
            null
        }

        state.bevelShader = null
    }
}

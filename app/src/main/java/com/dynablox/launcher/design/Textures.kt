package com.dynablox.launcher.design

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import java.util.Random

/**
 * Procedural micro-textures. Everything is generated once at runtime from a fixed seed, so the
 * APK ships no bitmap assets and the materials stay crisp at any density.
 *
 * The textures are deliberately subtle (alpha <= ~10%): they read as material, never as noise.
 */
object Textures {

    private val cache = HashMap<String, Bitmap>()
    private val lock = Any()

    /** Fine film grain used by ceramic, glass and rubber. */
    fun noise(size: Int = 96, contrast: Int = 20, seed: Long = 1337L): Bitmap =
        cached("noise-$size-$contrast-$seed") {
            val random = Random(seed)
            val pixels = IntArray(size * size)
            for (i in pixels.indices) {
                val v = 128 + random.nextInt(contrast * 2 + 1) - contrast
                val a = random.nextInt(contrast + 10)
                pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }

    /**
     * Anisotropic streaks for brushed aluminium. The pattern varies only vertically so it can be
     * tiled horizontally forever without a visible seam.
     */
    fun brushed(height: Int = 384, contrast: Int = 24, seed: Long = 91L): Bitmap =
        cached("brushed-$height-$contrast-$seed") {
            val width = 6
            val random = Random(seed)
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val base = 128 + random.nextInt(contrast * 2 + 1) - contrast
                val alpha = 6 + random.nextInt(contrast)
                for (x in 0 until width) {
                    val jitter = random.nextInt(7) - 3
                    val v = (base + jitter).coerceIn(0, 255)
                    pixels[y * width + x] = (alpha shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

    /** Micro-dotted rubber grip. */
    fun microDot(size: Int = 7, dotAlpha: Int = 26, seed: Long = 5L): Bitmap =
        cached("dot-$size-$dotAlpha-$seed") {
            val random = Random(seed)
            val pixels = IntArray(size * size)
            val c = size / 2f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val d = Math.hypot((x - c).toDouble(), (y - c).toDouble()).toFloat()
                    val a = if (d < size * 0.34f) dotAlpha else random.nextInt(dotAlpha / 3 + 1)
                    val v = if (d < size * 0.34f) 210 else 90
                    pixels[y * size + x] = (a shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }

    /** Knurling pattern for rotary knobs and fader caps. */
    fun knurl(height: Int = 64, seed: Long = 21L): Bitmap =
        cached("knurl-$height-$seed") {
            val width = 4
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val ridge = (x == 0)
                    val v = if (ridge) 235 else 60
                    val a = if (ridge) 46 else 34
                    pixels[y * width + x] = (a shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

    fun shader(bitmap: Bitmap, alpha: Int = 255): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        this.alpha = alpha
    }

    /** Releases every cached bitmap (used by the optimizer's memory reclaim action). */
    fun trim() {
        synchronized(lock) {
            cache.values.forEach { if (!it.isRecycled) it.recycle() }
            cache.clear()
        }
    }

    private fun cached(key: String, create: () -> Bitmap): Bitmap = synchronized(lock) {
        val existing = cache[key]
        if (existing != null && !existing.isRecycled) {
            existing
        } else {
            val bitmap = create()
            cache[key] = bitmap
            bitmap
        }
    }
}

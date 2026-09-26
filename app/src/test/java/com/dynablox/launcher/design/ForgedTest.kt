package com.dynablox.launcher.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry rules of the FORGED finish.
 *
 * Only the primitive overload is tested: unit tests run on the JVM, where every `android.graphics`
 * class is a stub that throws. The painter is therefore split so the rule that actually matters —
 * how big the corner cut is — can be verified without a Canvas.
 */
class ForgedTest {

    @Test
    fun `chamfer grows with the requested radius`() {
        val small = Forged.chamfer(400f, 200f, 4f)
        val large = Forged.chamfer(400f, 200f, 16f)
        assertTrue("cut should scale with radius", large > small)
    }

    @Test
    fun `chamfer never eats more than a third of the shortest side`() {
        // Without the cap a small square key would lose its whole face and read as a diamond.
        val cut = Forged.chamfer(48f, 48f, 500f)
        assertTrue(cut <= 48f * 0.30f + 0.001f)
    }

    @Test
    fun `chamfer is driven by the shortest side`() {
        // A wide, short plate must be capped by its height, not its width.
        assertEquals(Forged.chamfer(2000f, 20f, 500f), Forged.chamfer(30f, 20f, 500f), 0.001f)
    }

    @Test
    fun `degenerate boxes produce no cut`() {
        // Views are measured 0x0 before the first layout pass; the painter must not emit a
        // negative cut that would invert the plate outline.
        assertEquals(0f, Forged.chamfer(0f, 0f, 24f), 0f)
        assertEquals(0f, Forged.chamfer(-10f, 50f, 24f), 0f)
    }

    @Test
    fun `chamfer is never negative`() {
        assertTrue(Forged.chamfer(100f, 100f, 0f) >= 0f)
        assertTrue(Forged.chamfer(100f, 100f, -50f) >= 0f)
    }

    @Test
    fun `a plate always keeps a visible face between its cuts`() {
        // Two opposite cuts must not meet: 2 * cut < shortest side, or there is no flat edge left.
        listOf(24f to 24f, 48f to 96f, 320f to 64f).forEach { (w, h) ->
            val cut = Forged.chamfer(w, h, 999f)
            assertTrue("cuts collide at ${w}x$h", cut * 2f < minOf(w, h))
        }
    }

    @Test
    fun `chamfer is monotonic across sizes`() {
        var previous = -1f
        listOf(10f, 20f, 40f, 80f, 160f).forEach { side ->
            val cut = Forged.chamfer(side, side, 12f)
            assertTrue(cut >= previous)
            previous = cut
        }
    }
}

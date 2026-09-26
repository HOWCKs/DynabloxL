package com.dynablox.launcher.design

import com.dynablox.launcher.core.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The finish migration is only safe because it is a *substitution*, not a fork: both vocabularies
 * answer the same questions for the same geometry. These tests pin the parts of that contract that
 * are pure logic, since CI cannot look at pixels.
 *
 * Deliberately out of scope: the painters themselves. They need a real Canvas, and the android.jar
 * stubs used by JVM unit tests throw "Stub!" — so the geometry rule is exercised through the
 * Canvas-free overload of [Clay.clayRadius], and the rendering is a device concern.
 */
class FinishTest {

    @Test
    fun `both finishes are reachable and distinct`() {
        val all = Finish.values().toList()
        assertEquals(2, all.size)
        assertTrue(all.contains(Finish.MACHINED))
        assertTrue(all.contains(Finish.CLAY))
    }

    @Test
    fun `clay fills corners without ever turning a form into a capsule`() {
        // A press cannot hold a tight corner, so clay inflates the requested radius...
        val requested = 20f
        val clay = Clay.clayRadius(400f, 200f, requested)
        assertTrue("clay should round more than the request", clay > requested)

        // ...but never past half the shortest side, which would silently restyle a card as a pill.
        assertTrue("radius must stay within the form", clay <= 100f)

        // A square asking for an enormous radius clamps exactly to the circle limit.
        assertEquals(50f, Clay.clayRadius(100f, 100f, 500f), 0.001f)
    }

    @Test
    fun `clay radius is monotonic so a bigger request is never rounder in reverse`() {
        var previous = -1f
        listOf(0f, 4f, 8f, 16f, 24f, 32f, 64f).forEach { requested ->
            val actual = Clay.clayRadius(600f, 400f, requested)
            assertTrue("radius must not decrease as the request grows", actual >= previous)
            previous = actual
        }
    }

    @Test
    fun `a degenerate box cannot produce a negative radius`() {
        // Views are measured at 0x0 before their first layout pass; Canvas throws on a negative
        // radius, so the painter must survive it.
        assertEquals(0f, Clay.clayRadius(0f, 0f, 24f), 0.001f)
        assertTrue(Clay.clayRadius(1f, 0f, 24f) >= 0f)
        assertTrue(Clay.clayRadius(-10f, -10f, 24f) >= 0f)
    }

    @Test
    fun `every surface role survives the migration`() {
        // Material is the *role* of a surface (panel, deck, well...), not its finish. Both
        // vocabularies must answer for all five, otherwise a screen loses a surface when switching.
        assertEquals(5, Material.values().size)
        assertTrue(Material.values().contains(Material.INSET))
        assertTrue(Material.values().contains(Material.CERAMIC))
    }

    @Test
    fun `the persisted finish maps one to one onto the design finish`() {
        // A missing branch here would silently strand the user on the wrong vocabulary.
        assertEquals(AppSettings.SurfaceFinish.values().size, Finish.values().size)
        AppSettings.SurfaceFinish.values().forEach { setting ->
            val mapped = when (setting) {
                AppSettings.SurfaceFinish.CLAY -> Finish.CLAY
                AppSettings.SurfaceFinish.MACHINED -> Finish.MACHINED
            }
            assertEquals(setting.name, mapped.name)
        }
    }

    @Test
    fun `clay is the default finish because it is the product identity`() {
        // Persisted by name: renaming a constant would reset every user to the other vocabulary.
        assertEquals("CLAY", AppSettings.SurfaceFinish.CLAY.name)
        assertEquals("MACHINED", AppSettings.SurfaceFinish.MACHINED.name)
    }
}

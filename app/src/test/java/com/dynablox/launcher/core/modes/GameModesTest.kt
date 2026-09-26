package com.dynablox.launcher.core.modes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalogue invariants.
 *
 * These are cheap to state and expensive to violate: a mode whose actions the Optimizer does not
 * know would silently do nothing, and a mode marked non-privileged that touches privileged state
 * would promise the user something Shizuku-less devices cannot deliver.
 */
class GameModesTest {

    /** Mirrors the ids declared in Optimizer. Kept here so a rename breaks a test, not a device. */
    private val knownActions = setOf(
        "battery", "cache", "kill_bg", "anim_scales", "force_stop", "dnd", "perf_hint", "thermal",
    )

    @Test
    fun `every action maps to a real optimizer entry`() {
        GameModes.ALL.forEach { mode ->
            mode.actions.forEach { action ->
                assertTrue("${mode.id} references unknown action $action", action in knownActions)
            }
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = GameModes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `off does nothing at all`() {
        assertTrue(GameModes.OFF.actions.isEmpty())
        assertEquals(0, GameModes.OFF.intensity)
        assertFalse(GameModes.OFF.needsShizuku)
    }

    @Test
    fun `rail never offers off as a choice`() {
        assertFalse(GameModes.SELECTABLE.any { it.id == GameModes.OFF.id })
        assertEquals(GameModes.ALL.size - 1, GameModes.SELECTABLE.size)
    }

    @Test
    fun `default mode requires no privilege`() {
        // The default has to work on a device where nothing has been granted, otherwise the
        // product's first impression is a permission wall.
        assertFalse(GameModes.DEFAULT.needsShizuku)
        assertEquals(GameModes.BALANCED.id, GameModes.DEFAULT.id)
    }

    @Test
    fun `privileged detection follows the action set`() {
        assertFalse(GameModes.ECO.needsShizuku)
        assertFalse(GameModes.BALANCED.needsShizuku)
        assertTrue(GameModes.TURBO.needsShizuku)
        assertTrue(GameModes.BATTLE.needsShizuku)
    }

    @Test
    fun `modes are cumulative in aggressiveness`() {
        // Each step up must be a superset: "more aggressive" has to mean "does more", or the rail
        // is just five unrelated presets wearing an intensity label.
        assertTrue(GameModes.BALANCED.actions.containsAll(GameModes.ECO.actions))
        assertTrue(GameModes.TURBO.actions.containsAll(GameModes.BALANCED.actions))
        assertTrue(GameModes.BATTLE.actions.containsAll(GameModes.TURBO.actions))
        assertTrue(GameModes.BATTLE.actions.size > GameModes.TURBO.actions.size)
    }

    @Test
    fun `intensity never decreases along the rail`() {
        val intensities = GameModes.ALL.map { it.intensity }
        assertEquals(intensities.sorted(), intensities)
        intensities.forEach { assertTrue(it in 0..3) }
    }

    @Test
    fun `thermal guard stays on for the aggressive modes`() {
        // A mode that ignores heat throttles the device into being slower than the mode it
        // replaced. BATTLE is allowed to be reckless with apps, never with temperature.
        assertTrue(GameModes.TURBO.thermalGuard)
        assertTrue(GameModes.BATTLE.thermalGuard)
        assertTrue(GameModes.BATTLE.actions.contains("thermal"))
    }

    @Test
    fun `unknown ids resolve to off rather than to a mode`() {
        assertEquals(GameModes.OFF.id, GameModes.byId(null).id)
        assertEquals(GameModes.OFF.id, GameModes.byId("").id)
        assertEquals(GameModes.OFF.id, GameModes.byId("turbo_v2").id)
        assertEquals(GameModes.BATTLE.id, GameModes.byId("battle").id)
    }

    @Test
    fun `each mode carries its own tint`() {
        val tints = GameModes.ALL.map { it.tint }
        assertEquals(tints.size, tints.toSet().size)
    }
}

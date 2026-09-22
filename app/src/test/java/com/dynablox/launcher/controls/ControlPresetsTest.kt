package com.dynablox.launcher.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pad is a remapper: every key stores the normalised screen coordinate it injects into. If a
 * preset ever ships a coordinate outside 0..1 the injected event lands off-screen, so the geometry
 * is asserted here rather than discovered in a game.
 */
class ControlPresetsTest {

    @Test
    fun `preset ids are unique and lookup falls back to the first preset`() {
        val ids = ControlPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(ControlPresets.ALL.first(), ControlPresets.byId("does-not-exist"))
        assertEquals(ControlPresets.ALL.first(), ControlPresets.byId(ControlPresets.ALL.first().id))
    }

    @Test
    fun `every preset has at least one cluster and every cluster has keys`() {
        ControlPresets.ALL.forEach { preset ->
            assertTrue("preset ${preset.id} has no clusters", preset.clusters.isNotEmpty())
            preset.clusters.forEach { cluster ->
                assertTrue("preset ${preset.id} has an empty cluster", cluster.buttons.isNotEmpty())
            }
        }
    }

    @Test
    fun `key geometry stays inside the cluster and the screen`() {
        ControlPresets.ALL.forEach { preset ->
            preset.clusters.forEach { cluster ->
                cluster.buttons.forEach { button ->
                    val where = "${preset.id}/${cluster.side}/${button.id}"
                    assertTrue("$where localX out of range: ${button.localX}", button.localX in 0f..1f)
                    assertTrue("$where localY out of range: ${button.localY}", button.localY in 0f..1f)
                    assertTrue("$where targetX off screen: ${button.targetX}", button.targetX in 0f..1f)
                    assertTrue("$where targetY off screen: ${button.targetY}", button.targetY in 0f..1f)
                    assertTrue("$where size must be positive: ${button.sizeDp}", button.sizeDp > 0f)
                }
            }
        }
    }

    @Test
    fun `key ids are unique inside a preset`() {
        ControlPresets.ALL.forEach { preset ->
            val ids = preset.clusters.flatMap { cluster -> cluster.buttons.map { it.id } }
            assertEquals("duplicate key id in ${preset.id}", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun `both thumb zones are represented somewhere in the catalogue`() {
        val sides = ControlPresets.ALL
            .flatMap { it.clusters }
            .map { it.side }
            .toSet()
        assertTrue(sides.contains(ClusterSide.LEFT))
        assertTrue(sides.contains(ClusterSide.RIGHT))
    }

    @Test
    fun `labels are declared for every key`() {
        ControlPresets.ALL.forEach { preset ->
            preset.clusters.forEach { cluster ->
                cluster.buttons.forEach { button ->
                    assertTrue(
                        "${preset.id}/${button.id} is missing a label resource",
                        button.labelRes != 0,
                    )
                }
            }
            assertTrue("${preset.id} is missing a name resource", preset.nameRes != 0)
            assertTrue("${preset.id} is missing a description resource", preset.descriptionRes != 0)
        }
    }

    @Test
    fun `the catalogue covers tap hold and the four swipes`() {
        val used = ControlPresets.ALL
            .flatMap { it.clusters }
            .flatMap { it.buttons }
            .map { it.gesture }
            .toSet()
        assertTrue(used.contains(PadGesture.TAP))
        assertTrue(used.contains(PadGesture.HOLD_SHORT))
        assertTrue(used.contains(PadGesture.HOLD_LONG))
        assertTrue(used.containsAll(listOf(
            PadGesture.SWIPE_UP, PadGesture.SWIPE_DOWN, PadGesture.SWIPE_LEFT, PadGesture.SWIPE_RIGHT,
        )))
    }

    @Test
    fun `system keys stay out of the pads - the hub owns them`() {
        // BACK / HOME / RECENTS are injected from MenuHub commands instead of a thumb cluster, so a
        // preset that never maps them is intentional. This test keeps that boundary explicit: if a
        // new gesture shows up it must be assigned to one of the two owners on purpose.
        val used = ControlPresets.ALL
            .flatMap { it.clusters }
            .flatMap { it.buttons }
            .map { it.gesture }
            .toSet()
        val systemKeys = setOf(PadGesture.KEY_BACK, PadGesture.KEY_HOME, PadGesture.KEY_RECENTS)
        val unmapped = PadGesture.values().filter { it !in used }.toSet()
        assertTrue("unmapped gestures must be system keys: $unmapped", systemKeys.containsAll(unmapped))
    }
}

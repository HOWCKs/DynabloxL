package com.dynablox.launcher.core.perf

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PerfSnapshot] is the single telemetry contract every surface renders from, so its derived values
 * (headroom, thermal wording, thermal severity) are covered here — the HUD, the orb LED ring and the
 * optimizer report all read them.
 */
class PerfSnapshotTest {

    @Test
    fun `headroom is measured against the panel refresh rate`() {
        val snapshot = PerfSnapshot(fps = 30f, refreshRate = 60f)
        assertEquals(0.5f, snapshot.headroom, 0.001f)
    }

    @Test
    fun `headroom clamps instead of reporting impossible values`() {
        assertEquals(0f, PerfSnapshot(fps = 60f, refreshRate = 0f).headroom, 0.001f)
        assertEquals(1.5f, PerfSnapshot(fps = 240f, refreshRate = 60f).headroom, 0.001f)
    }

    @Test
    fun `thermal wording never claims a state the API did not report`() {
        assertEquals("nominal", PerfSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE).thermalLabel)
        assertEquals("nominal", PerfSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_LIGHT).thermalLabel)
        assertEquals("warm", PerfSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_MODERATE).thermalLabel)
        assertEquals("throttling", PerfSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_SEVERE).thermalLabel)
        assertEquals("critical", PerfSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL).thermalLabel)
        assertEquals("unknown", PerfSnapshot(thermalStatus = -1).thermalLabel)
    }

    @Test
    fun `thermal severity is ordered`() {
        val severities = listOf(
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE,
            PowerManager.THERMAL_STATUS_SEVERE,
        ).map { PerfSnapshot(thermalStatus = it).thermalSeverity }
        assertEquals(severities.sorted(), severities)
        assertTrue(severities.last() > severities.first())
    }

    @Test
    fun `an empty snapshot reports nothing instead of zeros that look measured`() {
        val empty = PerfSnapshot()
        assertEquals(0f, empty.fps, 0.0001f)
        assertEquals(FpsSourceKind.NONE, empty.fpsSource)
        assertEquals(-1, empty.batteryPercent)
        assertTrue(empty.corePercents.isEmpty())
    }

    @Test
    fun `fps source labels are shown verbatim in the hud`() {
        assertEquals("choreographer", FpsSourceKind.CHOREOGRAPHER.label)
        assertEquals("surfaceflinger", FpsSourceKind.SURFACE_FLINGER.label)
        assertEquals("no source", FpsSourceKind.NONE.label)
    }
}

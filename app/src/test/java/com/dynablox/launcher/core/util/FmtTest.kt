package com.dynablox.launcher.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formatting is user-visible telemetry, so it is pinned by tests: the HUD must never print a
 * negative duration, a NaN frame time or a layer name that overflows its readout.
 *
 * Assertions that involve decimal separators compare against the same `String.format` call the
 * production code uses, so they hold in every locale.
 */
class FmtTest {

    @Test
    fun `number keeps the requested decimals`() {
        assertEquals("%.0f".format(59.4f), Fmt.number(59.4f, 0))
        assertEquals("%.2f".format(16.666f), Fmt.number(16.666f, 2))
        assertEquals("42", Fmt.number(42))
    }

    @Test
    fun `percent always carries the sign`() {
        assertTrue(Fmt.percent(12.5f).endsWith("%"))
        assertTrue(Fmt.percent(12.5f).contains("12"))
    }

    @Test
    fun `bytes walks the unit ladder`() {
        assertEquals("—", Fmt.bytes(0L))
        assertEquals("—", Fmt.bytes(-5L))
        assertEquals("512 B", Fmt.bytes(512L))
        assertEquals("1 KB", Fmt.bytes(1024L))
        assertEquals("%.1f MB".format(1.5f), Fmt.bytes(1024L * 1024L * 3 / 2))
        assertTrue(Fmt.bytes(3L * 1024L * 1024L * 1024L).endsWith("GB"))
    }

    @Test
    fun `rate never shows a negative throughput`() {
        assertEquals("0 KB/s", Fmt.rate(0f))
        assertEquals("0 KB/s", Fmt.rate(-20f))
        assertTrue(Fmt.rate(2048f).endsWith("KB/s"))
        assertTrue(Fmt.rate(4f * 1024f * 1024f).endsWith("MB/s"))
    }

    @Test
    fun `celsius reads tenths of a degree from the battery extra`() {
        assertEquals("%.1f°C".format(3.7f), Fmt.celsius(37))
        assertEquals("%.1f°C".format(41.2f), Fmt.celsius(41.2f))
    }

    @Test
    fun `duration is mms below an hour and hmmss above`() {
        assertEquals("0:00", Fmt.duration(0L))
        assertEquals("1:30", Fmt.duration(90L))
        assertEquals("1:02:05", Fmt.duration(3725L))
        assertEquals("0:00", Fmt.duration(-400L))
    }

    @Test
    fun `relative time degrades from seconds to days`() {
        val now = 1_700_000_000_000L
        assertEquals("—", Fmt.relativeTime(0L, now))
        assertEquals("30s", Fmt.relativeTime(now - 30_000L, now))
        assertEquals("5m", Fmt.relativeTime(now - 5 * 60_000L, now))
        assertEquals("3h", Fmt.relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("2d", Fmt.relativeTime(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `layer name strips the surfaceflinger suffixes`() {
        assertEquals("com.roblox.client", Fmt.layerName("com.roblox.client/com.roblox.client.MainActivity#0"))
        assertEquals("SurfaceView[com.roblox.client]", Fmt.layerName("SurfaceView[com.roblox.client]#1"))
        assertEquals("plain", Fmt.layerName("plain"))
    }

    @Test
    fun `log scale clamps outside the range`() {
        assertEquals(0f, Fmt.logScale(0f, 1f, 100f), 0.0001f)
        assertEquals(1f, Fmt.logScale(500f, 1f, 100f), 0.0001f)
        assertEquals(0f, Fmt.logScale(10f, 10f, 10f), 0.0001f)
        val mid = Fmt.logScale(10f, 1f, 100f)
        assertTrue(mid in 0.45f..0.55f)
    }

    @Test
    fun `pow matches the double implementation`() {
        assertEquals(8f, Fmt.pow(2f, 3f), 0.0001f)
    }

    @Test
    fun `megabytes keeps the unit attached`() {
        assertTrue(Fmt.megabytes(1024.75f, 1).endsWith("MB"))
    }
}

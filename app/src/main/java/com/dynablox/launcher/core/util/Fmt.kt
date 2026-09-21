package com.dynablox.launcher.core.util

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Formatting helpers shared by the HUD, the launcher deck and notifications. */
object Fmt {

    fun percent(value: Float, decimals: Int = 0): String =
        "%.${decimals}f%%".format(value)

    fun bytes(value: Long): String = when {
        value <= 0L -> "—"
        value < 1024L -> "$value B"
        value < 1024L * 1024L -> "${(value / 1024f).roundToInt()} KB"
        value < 1024L * 1024L * 1024L -> "%.1f MB".format(value / (1024f * 1024f))
        else -> "%.2f GB".format(value / (1024f * 1024f * 1024f))
    }

    fun megabytes(valueMb: Float, decimals: Int = 0): String = "%.${decimals}f MB".format(valueMb)

    fun rate(bytesPerSecond: Float): String = when {
        bytesPerSecond <= 0f -> "0 KB/s"
        bytesPerSecond < 1024f -> "${bytesPerSecond.roundToInt()} B/s"
        bytesPerSecond < 1024f * 1024f -> "%.0f KB/s".format(bytesPerSecond / 1024f)
        else -> "%.1f MB/s".format(bytesPerSecond / (1024f * 1024f))
    }

    fun celsius(tenthOfDegree: Int): String = "%.1f°C".format(tenthOfDegree / 10f)

    fun celsius(value: Float): String = "%.1f°C".format(value)

    fun ms(value: Float, decimals: Int = 1): String = "%.${decimals}f ms".format(value)

    /** 90 -> "1:30", 3725 -> "1:02:05". */
    fun duration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun clockTime(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(millis))

    fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return "—"
        val delta = abs(now - millis) / 1000L
        return when {
            delta < 60 -> "${delta}s"
            delta < 3600 -> "${delta / 60}m"
            delta < 86400 -> "${delta / 3600}h"
            else -> "${delta / 86400}d"
        }
    }

    /** Shortens a SurfaceFlinger layer name for display. */
    fun layerName(raw: String): String {
        val hash = raw.indexOf('#')
        val base = if (hash > 0) raw.substring(0, hash) else raw
        val slash = base.indexOf('/')
        return if (slash > 0) base.substring(0, slash) else base
    }

    /** Compact log scale helper for bar meters. */
    fun logScale(value: Float, min: Float, max: Float): Float {
        if (value <= min) return 0f
        if (value >= max) return 1f
        val range = ln(max / min)
        if (abs(range) < 0.0001f) return 0f
        return (ln(value / min) / range).coerceIn(0f, 1f)
    }

    fun pow(base: Float, exponent: Float): Float = base.toDouble().pow(exponent.toDouble()).toFloat()
}

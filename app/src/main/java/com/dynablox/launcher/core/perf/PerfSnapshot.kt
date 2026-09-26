package com.dynablox.launcher.core.perf

import android.os.PowerManager

/** Where the current FPS number came from — surfaced verbatim in the HUD so nothing is implied. */
enum class FpsSourceKind(val label: String) {
    NONE("no source"),
    CHOREOGRAPHER("choreographer"),
    SURFACE_FLINGER("surfaceflinger"),
}

/** One immutable telemetry sample. Every field is measured, never simulated. */
data class PerfSnapshot(
    val timestamp: Long = 0L,
    val fps: Float = 0f,
    val fpsSource: FpsSourceKind = FpsSourceKind.NONE,
    val fpsLayer: String? = null,
    val frameTimeMs: Float = 0f,
    val droppedFrames: Int = 0,
    val refreshRate: Float = 60f,
    val cpuPercent: Float = 0f,
    val corePercents: List<Float> = emptyList(),
    val coreFrequenciesMhz: List<Int> = emptyList(),
    val gpuPercent: Float? = null,
    val ramUsedMb: Float = 0f,
    val ramTotalMb: Float = 0f,
    val ramPercent: Float = 0f,
    val batteryPercent: Int = -1,
    val batteryTemperatureC: Float = 0f,
    val batteryCharging: Boolean = false,
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val netDownKbps: Float = 0f,
    val netUpKbps: Float = 0f,
    val pingMs: Float? = null,
    val sessionSeconds: Long = 0L,
    val bestFps: Float = 0f,
    val worstFps: Float = 0f,
) {
    /** Ratio of measured FPS to the panel refresh rate — 1.0 means every vsync was used. */
    val headroom: Float
        get() = if (refreshRate <= 1f) 0f else (fps / refreshRate).coerceIn(0f, 1.5f)

    val thermalLabel: String
        get() = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> "nominal"
            PowerManager.THERMAL_STATUS_MODERATE -> "warm"
            PowerManager.THERMAL_STATUS_SEVERE -> "throttling"
            PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY -> "critical"
            else -> "unknown"
        }

    val thermalSeverity: Int
        get() = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 0
            PowerManager.THERMAL_STATUS_LIGHT -> 1
            PowerManager.THERMAL_STATUS_MODERATE -> 2
            else -> 3
        }
}

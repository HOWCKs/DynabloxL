package com.dynablox.launcher.core.perf

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.shizuku.ShizukuController
import com.dynablox.launcher.core.util.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The telemetry engine behind the floating HUD, the launcher gauges and the optimizer report.
 *
 * Sampling is layered so each source runs at the rate it deserves: cheap /proc and sysfs reads at the
 * user-selected refresh, the `dumpsys SurfaceFlinger` probe (which spawns a shell process) at a fixed
 * 600 ms, and the TCP latency probe every few seconds only when enabled.
 */
class PerfEngine(
    private val context: Context,
    private val settings: AppSettings,
    private val shizuku: ShizukuController,
    private val device: DeviceInfo,
) {

    private val _snapshot = MutableStateFlow(PerfSnapshot(refreshRate = device.currentRefreshRate))
    val snapshot: StateFlow<PerfSnapshot> = _snapshot

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val cpu = CpuSampler()
    private val gpu = GpuSampler()
    private val memory = MemorySampler(context)
    private val battery = BatterySampler(context)
    private val network = NetworkSampler()
    private val choreographer = ChoreographerFps()
    private val surfaceFlinger = SurfaceFlingerFps(shizuku, TARGET_PACKAGE)
    private val latency = LatencyProbe()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val thermalExecutor: Executor = Executors.newSingleThreadExecutor()
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var scope: CoroutineScope? = null
    private var metricsJob: Job? = null
    private var surfaceJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    private var paused = false

    @Volatile
    private var thermalStatus = PowerManager.THERMAL_STATUS_NONE

    @Volatile
    private var sfReading: SurfaceFlingerFps.Reading? = null

    @Volatile
    private var pingMs: Float? = null

    private var sessionStart = 0L
    private var bestFps = 0f
    private var worstFps = Float.MAX_VALUE

    // Created lazily: PowerManager.OnThermalStatusChangedListener only exists on API 29+.
    private val thermalListener by lazy {
        PowerManager.OnThermalStatusChangedListener { status -> thermalStatus = status }
    }

    val isRunning: Boolean get() = _running.value

    fun start() {
        if (_running.value) return
        _running.value = true
        sessionStart = SystemClock.elapsedRealtime()
        bestFps = 0f
        worstFps = Float.MAX_VALUE
        cpu.reset()
        network.reset()
        choreographer.resetDrops()
        battery.start()
        surfaceFlinger.setTargetPackage(TARGET_PACKAGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                powerManager.addThermalStatusListener(thermalExecutor, thermalListener)
                thermalStatus = powerManager.currentThermalStatus
            } catch (t: Throwable) {
                Log.w(TAG, "thermal listener unavailable", t)
            }
        }

        mainHandler.post {
            if (choreographerWanted()) choreographer.start(device.maxRefreshRate)
        }

        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = coroutineScope
        metricsJob = coroutineScope.launch { metricsLoop() }
        surfaceJob = coroutineScope.launch { surfaceFlingerLoop() }
        pingJob = coroutineScope.launch { latencyLoop() }
    }

    fun stop() {
        if (!_running.value) return
        _running.value = false
        metricsJob?.cancel()
        surfaceJob?.cancel()
        pingJob?.cancel()
        scope?.cancel()
        scope = null
        mainHandler.post { choreographer.stop() }
        battery.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                powerManager.removeThermalStatusListener(thermalListener)
            } catch (_: Throwable) {
                // Already removed.
            }
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun resetSession() {
        sessionStart = SystemClock.elapsedRealtime()
        bestFps = 0f
        worstFps = Float.MAX_VALUE
        choreographer.resetDrops()
    }

    private fun choreographerWanted(): Boolean = when (settings.hudFpsSource) {
        AppSettings.FpsSource.CHOREOGRAPHER -> true
        AppSettings.FpsSource.SURFACE_FLINGER -> false
        AppSettings.FpsSource.AUTO -> !shizuku.isGranted
    }

    private suspend fun metricsLoop() {
        while (scope?.isActive == true) {
            val interval = settings.hudRefreshMs.coerceIn(250L, 5000L)
            if (!paused) publish()
            delay(interval)
        }
    }

    private suspend fun surfaceFlingerLoop() {
        while (scope?.isActive == true) {
            if (!paused && shizuku.isGranted && settings.hudFpsSource != AppSettings.FpsSource.CHOREOGRAPHER) {
                try {
                    sfReading = surfaceFlinger.sample()
                } catch (t: Throwable) {
                    Log.w(TAG, "surfaceflinger sample failed", t)
                    sfReading = null
                }
            } else {
                sfReading = null
            }
            delay(SURFACE_FLINGER_INTERVAL_MS)
        }
    }

    private suspend fun latencyLoop() {
        while (scope?.isActive == true) {
            if (!paused && settings.hudPingEnabled) {
                pingMs = latency.measure(settings.hudPingHost)
            } else {
                pingMs = null
            }
            delay(LATENCY_INTERVAL_MS)
        }
    }

    private fun publish() {
        val cpuReading = cpu.sample()
        val memReading = memory.sample()
        val battReading = battery.sample()
        val netReading = network.sample()
        val gpuPercent = gpu.sample()
        val refresh = device.currentRefreshRate

        // Keep the frame-pacing source in sync with the user's preference.
        val wantsChoreographer = choreographerWanted()
        if (wantsChoreographer && !choreographer.running) {
            mainHandler.post { choreographer.start(refresh) }
        } else if (!wantsChoreographer && choreographer.running) {
            mainHandler.post { choreographer.stop() }
        }

        val sf = sfReading
        val useSf = settings.hudFpsSource != AppSettings.FpsSource.CHOREOGRAPHER && sf != null && sf.fps > 0f
        val fps = if (useSf) sf!!.fps else choreographer.fps
        val frameTime = if (useSf) sf!!.frameTimeMs else choreographer.frameTimeMs
        val source = when {
            useSf -> FpsSourceKind.SURFACE_FLINGER
            choreographer.running && choreographer.fps > 0f -> FpsSourceKind.CHOREOGRAPHER
            else -> FpsSourceKind.NONE
        }

        if (fps > 0f) {
            if (fps > bestFps) bestFps = fps
            if (fps < worstFps) worstFps = fps
        }

        var thermal = thermalStatus
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            thermal = when {
                battReading.temperatureC >= 45f -> PowerManager.THERMAL_STATUS_CRITICAL
                battReading.temperatureC >= 41f -> PowerManager.THERMAL_STATUS_SEVERE
                battReading.temperatureC >= 38f -> PowerManager.THERMAL_STATUS_MODERATE
                battReading.temperatureC >= 35f -> PowerManager.THERMAL_STATUS_LIGHT
                else -> PowerManager.THERMAL_STATUS_NONE
            }
        }

        val next = PerfSnapshot(
            timestamp = SystemClock.elapsedRealtime(),
            fps = fps,
            fpsSource = source,
            fpsLayer = sf?.layer,
            frameTimeMs = frameTime,
            droppedFrames = if (useSf) 0 else choreographer.droppedFrames,
            refreshRate = if (refresh > 1f) refresh else device.maxRefreshRate,
            cpuPercent = cpuReading.total,
            corePercents = cpuReading.cores,
            coreFrequenciesMhz = cpu.coreFrequencies(device.cores),
            gpuPercent = gpuPercent,
            ramUsedMb = memReading.usedMb,
            ramTotalMb = memReading.totalMb,
            ramPercent = memReading.percent,
            batteryPercent = battReading.percent,
            batteryTemperatureC = battReading.temperatureC,
            batteryCharging = battReading.charging,
            thermalStatus = thermal,
            netDownKbps = netReading.downKbps,
            netUpKbps = netReading.upKbps,
            pingMs = pingMs,
            sessionSeconds = if (sessionStart == 0L) 0L else (SystemClock.elapsedRealtime() - sessionStart) / 1000L,
            bestFps = bestFps,
            worstFps = if (worstFps == Float.MAX_VALUE) 0f else worstFps,
        )
        _snapshot.value = next
    }

    companion object {
        private const val TAG = "PerfEngine"
        private const val SURFACE_FLINGER_INTERVAL_MS = 600L
        private const val LATENCY_INTERVAL_MS = 4_000L
        const val TARGET_PACKAGE = "com.roblox.client"
    }
}

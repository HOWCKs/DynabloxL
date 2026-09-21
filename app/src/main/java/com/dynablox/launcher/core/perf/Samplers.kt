package com.dynablox.launcher.core.perf

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.dynablox.launcher.core.shizuku.ShizukuController
import com.dynablox.launcher.core.util.DeviceInfo
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Aggregated + per-core CPU utilisation derived from /proc/stat deltas. */
class CpuSampler {

    data class Reading(val total: Float, val cores: List<Float>)

    private var prevTotal = -1L
    private var prevIdle = -1L
    private var prevCoreTotal = LongArray(0)
    private var prevCoreIdle = LongArray(0)

    fun sample(): Reading {
        val lines = try {
            File("/proc/stat").useLines { seq -> seq.take(64).toList() }
        } catch (_: Throwable) {
            return Reading(0f, emptyList())
        }

        var total = 0f
        val cores = ArrayList<Float>()
        val coreTotals = ArrayList<Long>()
        val coreIdles = ArrayList<Long>()

        lines.forEach { line ->
            if (!line.startsWith("cpu")) return@forEach
            val parts = line.split(WHITESPACE).filter { it.isNotBlank() }
            if (parts.size < 5) return@forEach
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return@forEach
            val idle = values[3] + (values.getOrNull(4) ?: 0L)
            val sum = values.sum()
            if (parts[0] == "cpu") {
                if (prevTotal >= 0) {
                    val dTotal = sum - prevTotal
                    val dIdle = idle - prevIdle
                    total = if (dTotal > 0) ((dTotal - dIdle) * 100f / dTotal) else 0f
                }
                prevTotal = sum
                prevIdle = idle
            } else {
                val index = coreTotals.size
                if (index < prevCoreTotal.size && index < prevCoreIdle.size) {
                    val dTotal = sum - prevCoreTotal[index]
                    val dIdle = idle - prevCoreIdle[index]
                    cores.add(if (dTotal > 0) ((dTotal - dIdle) * 100f / dTotal) else 0f)
                }
                coreTotals.add(sum)
                coreIdles.add(idle)
            }
        }

        prevCoreTotal = coreTotals.toLongArray()
        prevCoreIdle = coreIdles.toLongArray()
        return Reading(total.coerceIn(0f, 100f), cores)
    }

    /** Live frequency of each core in MHz (0 when the sysfs node is not readable). */
    fun coreFrequencies(coreCount: Int): List<Int> {
        val result = ArrayList<Int>(coreCount)
        for (i in 0 until coreCount) {
            val khz = DeviceInfo.readFirstInt("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            result.add(if (khz == null) 0 else (khz / 1000L).toInt())
        }
        return result
    }

    fun maxFrequency(coreIndex: Int): Int {
        val khz = DeviceInfo.readFirstInt("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/cpuinfo_max_freq")
        return if (khz == null) 0 else (khz / 1000L).toInt()
    }

    fun reset() {
        prevTotal = -1L
        prevIdle = -1L
        prevCoreTotal = LongArray(0)
        prevCoreIdle = LongArray(0)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** Best-effort GPU load from vendor sysfs nodes. Null when nothing is readable. */
class GpuSampler {

    private var resolvedPath: String? = null
    private var resolutionTried = false
    private var gpubusyMode = false

    fun sample(): Float? {
        val path = resolve() ?: return null
        val raw = DeviceInfo.readFile(path)?.trim() ?: return null
        return try {
            if (gpubusyMode) {
                val parts = raw.split(WHITESPACE).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 2 && parts[1] > 0) (parts[0] * 100f / parts[1]).coerceIn(0f, 100f) else null
            } else {
                raw.toFloatOrNull()?.coerceIn(0f, 100f)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolve(): String? {
        if (resolutionTried) return resolvedPath
        resolutionTried = true

        val direct = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/devfreq/gpu/load",
        )
        direct.firstOrNull { File(it).canRead() }?.let {
            resolvedPath = it
            return it
        }

        val busy = "/sys/class/kgsl/kgsl-3d0/gpubusy"
        if (File(busy).canRead()) {
            gpubusyMode = true
            resolvedPath = busy
            return busy
        }

        // Mali / other devfreq devices: scan for a utilisation or load node.
        try {
            val devfreq = File("/sys/class/devfreq")
            if (devfreq.isDirectory) {
                devfreq.listFiles()?.forEach { entry ->
                    listOf("utilisation", "load").forEach { leaf ->
                        val candidate = File(entry, leaf)
                        if (candidate.canRead()) {
                            resolvedPath = candidate.absolutePath
                            return candidate.absolutePath
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore: GPU load simply stays unavailable.
        }
        resolvedPath = null
        return null
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** RAM from ActivityManager (the same numbers the system settings screen shows). */
class MemorySampler(context: Context) {

    data class Reading(val usedMb: Float, val totalMb: Float, val percent: Float)

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val info = ActivityManager.MemoryInfo()

    fun sample(): Reading {
        return try {
            am.getMemoryInfo(info)
            val total = info.totalMem
            val avail = info.availMem
            val used = (total - avail).coerceAtLeast(0L)
            Reading(
                usedMb = used / MB,
                totalMb = total / MB,
                percent = if (total > 0) used * 100f / total else 0f,
            )
        } catch (_: Throwable) {
            Reading(0f, 0f, 0f)
        }
    }

    private companion object {
        const val MB = 1024f * 1024f
    }
}

/** Battery level, charging state and pack temperature from the sticky battery broadcast. */
class BatterySampler(context: Context) {

    data class Reading(val percent: Int, val temperatureC: Float, val charging: Boolean)

    private val appContext = context.applicationContext
    private var last = Reading(-1, 0f, false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val extras: Bundle = intent?.extras ?: return
            val level = extras.getInt(BatteryManager.EXTRA_LEVEL, -1)
            val scale = extras.getInt(BatteryManager.EXTRA_SCALE, 100)
            val tenths = extras.getInt(BatteryManager.EXTRA_TEMPERATURE, 0)
            val status = extras.getInt(BatteryManager.EXTRA_STATUS, BatteryManager.UNKNOWN)
            val plugged = extras.getInt(BatteryManager.EXTRA_PLUGGED, 0)
            last = Reading(
                percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1,
                temperatureC = tenths / 10f,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0,
            )
        }
    }

    fun start() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = ContextCompat.registerReceiver(
                appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (sticky != null) receiver.onReceive(appContext, sticky)
        } catch (_: Throwable) {
            // Sampling stays unavailable; the HUD shows "—".
        }
    }

    fun stop() {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // Already unregistered.
        }
    }

    fun sample(): Reading = last
}

/** Throughput from TrafficStats deltas. */
class NetworkSampler {

    data class Reading(val downKbps: Float, val upKbps: Float)

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    fun sample(): Reading {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return Reading(0f, 0f)
        }
        val reading = if (lastRx < 0 || lastAt == 0L) {
            Reading(0f, 0f)
        } else {
            val seconds = (now - lastAt) / 1000f
            if (seconds <= 0.05f) {
                Reading(0f, 0f)
            } else {
                val down = ((rx - lastRx).coerceAtLeast(0L) / seconds) / 1024f * 8f
                val up = ((tx - lastTx).coerceAtLeast(0L) / seconds) / 1024f * 8f
                Reading(down, up)
            }
        }
        lastRx = rx
        lastTx = tx
        lastAt = now
        return reading
    }

    fun reset() {
        lastRx = -1L
        lastTx = -1L
        lastAt = 0L
    }
}

/**
 * Frame pacing measured from Choreographer callbacks on the main looper.
 *
 * This is an honest *estimate*: it reports how many vsyncs the app process actually serviced, so it
 * drops when the system is stuttering, but it cannot see another process's swap chain. When Shizuku
 * is available the engine prefers [SurfaceFlingerFps], which reads the real presented timestamps of
 * the game layer.
 */
class ChoreographerFps {

    @Volatile
    var fps: Float = 0f
        private set

    @Volatile
    var frameTimeMs: Float = 0f
        private set

    @Volatile
    var droppedFrames: Int = 0
        private set

    @Volatile
    var running: Boolean = false
        private set

    private var frames = 0
    private var windowStart = 0L
    private var lastFrameNanos = 0L
    private var budgetMs = 16.7f
    private var totalFrames = 0
    private var totalDropped = 0

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNanos != 0L) {
                val intervalMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                frameTimeMs = intervalMs
                totalFrames++
                if (intervalMs > budgetMs * 1.75f) {
                    totalDropped++
                    droppedFrames = totalDropped
                }
            }
            lastFrameNanos = frameTimeNanos
            frames++
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - windowStart
            if (elapsed >= 500L) {
                fps = frames * 1000f / elapsed
                frames = 0
                windowStart = now
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Must be called from a looper thread (the engine posts it to the main looper). */
    fun start(refreshRate: Float) {
        if (running) return
        budgetMs = if (refreshRate > 1f) 1000f / refreshRate else 16.7f
        running = true
        frames = 0
        windowStart = SystemClock.elapsedRealtime()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        fps = 0f
        frameTimeMs = 0f
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    fun resetDrops() {
        totalDropped = 0
        droppedFrames = 0
    }
}

/** Reads the real presented-frame timestamps of the game layer through `dumpsys SurfaceFlinger`. */
class SurfaceFlingerFps(
    private val shizuku: ShizukuController,
    private var targetPackage: String,
) {

    data class Reading(
        val fps: Float,
        val frameTimeMs: Float,
        val layer: String,
        val elapsedMs: Long,
    )

    private var cachedLayer: String? = null
    private var layerListedAt = 0L

    fun setTargetPackage(pkg: String) {
        if (targetPackage != pkg) {
            targetPackage = pkg
            cachedLayer = null
        }
    }

    suspend fun sample(): Reading? {
        if (!shizuku.isGranted) return null
        val layer = resolveLayer() ?: return null
        val result = shizuku.exec("dumpsys SurfaceFlinger --latency '$layer'")
        if (!result.succeeded) return null
        return parse(result.output, layer, result.elapsedMs)
    }

    private suspend fun resolveLayer(): String? {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedLayer
        if (cached != null && now - layerListedAt < LAYER_CACHE_MS) return cached

        val result = shizuku.exec("dumpsys SurfaceFlinger --list")
        if (!result.succeeded) return null
        layerListedAt = now
        val names = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return null

        val match = names.firstOrNull { it.contains(targetPackage, ignoreCase = true) }
            ?: names.firstOrNull { it.contains("SurfaceView", ignoreCase = true) }
            ?: names.firstOrNull { it.contains("GLSurfaceView", ignoreCase = true) }
        cachedLayer = match
        return match
    }

    /**
     * `--latency` prints the refresh period followed by up to 128 rows of
     * `desiredPresentTime actualPresentTime frameReadyTime` (nanoseconds, INT64_MAX when pending).
     */
    internal fun parse(output: String, layer: String, elapsedMs: Long): Reading? {
        val lines = output.lines()
        if (lines.size < 3) return null

        val presents = ArrayList<Long>(lines.size)
        val fallback = ArrayList<Long>(lines.size)
        lines.drop(1).forEach { line ->
            val parts = line.trim().split(WHITESPACE)
            if (parts.size < 2) return@forEach
            val first = parts.getOrNull(0)?.toLongOrNull()
            val second = parts.getOrNull(1)?.toLongOrNull()
            val third = parts.getOrNull(2)?.toLongOrNull()
            if (second != null && isValid(second)) presents.add(second)
            else if (third != null && isValid(third)) presents.add(third)
            if (first != null && isValid(first)) fallback.add(first)
        }

        val series = if (presents.size >= 3) presents else fallback
        if (series.size < 3) return null

        val first = series.first()
        val last = series.last()
        val spanNs = last - first
        if (spanNs <= 0) return null

        val fps = ((series.size - 1) * 1_000_000_000f / spanNs)
        if (fps <= 0f || fps > 480f) return null

        val intervals = ArrayList<Long>(series.size)
        for (i in 1 until series.size) intervals.add(series[i] - series[i - 1])
        intervals.sort()
        val medianNs = intervals[intervals.size / 2]
        return Reading(
            fps = fps,
            frameTimeMs = medianNs / 1_000_000f,
            layer = layer,
            elapsedMs = elapsedMs,
        )
    }

    private fun isValid(value: Long): Boolean = value > 0L && value < Long.MAX_VALUE / 2

    fun invalidate() {
        cachedLayer = null
        layerListedAt = 0L
    }

    private companion object {
        const val LAYER_CACHE_MS = 8_000L
        val WHITESPACE = Regex("\\s+")
    }
}

/** TCP connect latency to a host — the cheapest honest RTT without ICMP privileges. */
class LatencyProbe {

    suspend fun measure(host: String, port: Int = 443, timeoutMs: Int = 1200): Float? {
        if (host.isBlank()) return null
        return try {
            val socket = Socket()
            val started = SystemClock.elapsedRealtime()
            socket.use {
                it.connect(InetSocketAddress(host, port), timeoutMs)
                (SystemClock.elapsedRealtime() - started).toFloat()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Keeps a looper reference alive for callers that need the main thread check. */
    fun isMainThread(): Boolean = Looper.getMainLooper().isCurrentThread
}

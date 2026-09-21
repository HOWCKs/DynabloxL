package com.dynablox.launcher.core.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import android.view.WindowManager
import java.io.File

/** Static device facts, resolved once per process. */
class DeviceInfo(private val context: Context) {

    val manufacturer: String = Build.MANUFACTURER
    val model: String = Build.MODEL
    val device: String = Build.DEVICE
    val sdk: Int = Build.VERSION.SDK_INT
    val androidRelease: String = Build.VERSION.RELEASE

    val cores: Int = Runtime.getRuntime().availableProcessors()

    val totalRamBytes: Long by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem
    }

    val maxRefreshRate: Float by lazy {
        val display = displayOrNull()
        if (display == null) 60f else {
            val modes = display.supportedModes
            if (modes.isEmpty()) display.mode.refreshRate else modes.maxOf { it.refreshRate }
        }
    }

    val currentRefreshRate: Float
        get() = displayOrNull()?.mode?.refreshRate ?: 60f

    private fun displayOrNull(): Display? = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay
    } catch (_: Throwable) {
        null
    }

    fun freeStorageBytes(): Long = try {
        val stat = StatFs(File("/data").absolutePath)
        stat.availableBytes
    } catch (_: Throwable) {
        -1L
    }

    fun totalStorageBytes(): Long = try {
        val stat = StatFs(File("/data").absolutePath)
        stat.totalBytes
    } catch (_: Throwable) {
        -1L
    }

    fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    fun versionName(pkg: String): String? = try {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (_: Throwable) {
        null
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Best-effort GPU identity from sysfs; returns null when the vendor node is not readable. */
    fun gpuName(): String? {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/devfreq/soc:gpu/name",
            "/sys/devices/platform/mali.0/name",
        )
        candidates.forEach { path ->
            val value = readFile(path)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    fun summary(): String = "$manufacturer $model · Android $androidRelease (API $sdk) · ${cores}c"

    companion object {
        fun readFile(path: String): String? = try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (_: Throwable) {
            null
        }

        fun readFirstInt(path: String): Long? = readFile(path)?.trim()?.toLongOrNull()
    }
}

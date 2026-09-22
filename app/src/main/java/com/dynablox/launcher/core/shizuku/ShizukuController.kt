package com.dynablox.launcher.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Result of a shell command executed with shell (or root, via Sui) identity. */
data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val available: Boolean,
    val elapsedMs: Long,
) {
    val succeeded: Boolean get() = available && exitCode == 0
    val text: String get() = if (output.isNotEmpty()) output else error

    companion object {
        val UNAVAILABLE = ShellResult(-1, "", "shell identity unavailable", false, 0)
    }
}

enum class ShizukuStatus { UNKNOWN, MISSING, DENIED, GRANTED, ERROR }

/**
 * The single bridge to shell-level privileges.
 *
 * Everything privileged in DynabloxL — real SurfaceFlinger FPS, touch injection into games and the
 * reversible system tweaks in the optimizer — goes through here, and every feature degrades
 * gracefully when the binder is missing or the permission is denied.
 *
 * Sui (Magisk module) is preferred when present because it needs no per-boot restart; otherwise the
 * Shizuku provider is used.
 */
class ShizukuController(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.UNKNOWN)
    val status: StateFlow<ShizukuStatus> = _status

    private val _uid = MutableStateFlow(-1)
    val uid: StateFlow<Int> = _uid

    private val _apiVersion = MutableStateFlow(0)
    val apiVersion: StateFlow<Int> = _apiVersion

    /** "sui" (root), "shizuku" (shell) or "none". */
    private val _backend = MutableStateFlow("none")
    val backend: StateFlow<String> = _backend

    private var attached = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "permission result: granted=$granted")
        refresh()
    }

    fun attach() {
        if (attached) return
        attached = true
        // Sui ships in the Magisk module, not in the Shizuku API artifact, so it is bound by
        // reflection: present -> root identity without a per-boot restart, absent -> plain Shizuku.
        try {
            val sui = Class.forName("rikka.sui.Sui")
            val init = sui.getDeclaredMethod("init", String::class.java)
            if (init.invoke(null, context.packageName) == true) {
                _backend.value = "sui"
            }
        } catch (_: ClassNotFoundException) {
            // Sui not installed; Shizuku handles it.
        } catch (t: Throwable) {
            Log.w(TAG, "Sui init failed", t)
        }
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
            Log.w(TAG, "listener registration failed", t)
        }
        refresh()
    }

    fun detach() {
        if (!attached) return
        attached = false
        try {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
            // Nothing to do: the binder may already be gone.
        }
    }

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun refresh() {
        val alive = ping()
        if (!alive) {
            _status.value = ShizukuStatus.MISSING
            _uid.value = -1
            _apiVersion.value = 0
            return
        }
        try {
            if (_backend.value == "none") _backend.value = "shizuku"
            _apiVersion.value = Shizuku.getVersion()
            _uid.value = Shizuku.getUid()
            _status.value = when (Shizuku.checkSelfPermission()) {
                PackageManager.PERMISSION_GRANTED -> ShizukuStatus.GRANTED
                else -> ShizukuStatus.DENIED
            }
        } catch (t: Throwable) {
            Log.w(TAG, "refresh failed", t)
            _status.value = ShizukuStatus.ERROR
        }
    }

    val isGranted: Boolean get() = _status.value == ShizukuStatus.GRANTED

    /** Asks Shizuku for the API permission. Safe to call repeatedly. */
    fun requestPermission() {
        refresh()
        if (!ping()) return
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _status.value = ShizukuStatus.GRANTED
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                _status.value = ShizukuStatus.DENIED
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
            _status.value = ShizukuStatus.ERROR
        }
    }

    /** Blocking execution — call from a background thread or through [exec]. */
    fun execBlocking(command: String, maxOutputChars: Int = 32_768): ShellResult {
        if (!isGranted) return ShellResult.UNAVAILABLE
        val started = SystemClock.elapsedRealtime()
        return try {
            val process = shizukuProcess(arrayOf("sh", "-c", command))
            val output = process.inputStream.reader().readLimited(maxOutputChars)
            val error = process.errorStream.reader().readLimited(4096)
            val code = process.waitFor()
            ShellResult(
                exitCode = code,
                output = output,
                error = error,
                available = true,
                elapsedMs = SystemClock.elapsedRealtime() - started,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed: $command", t)
            ShellResult(-1, "", t.message ?: "shell failure", true, SystemClock.elapsedRealtime() - started)
        }
    }

    /**
     * [Shizuku.newProcess] is private in API 13 (it is scheduled for removal in 14) yet it remains
     * the only way to run a shell line through the Shizuku service, so it is reached reflectively.
     */
    private fun shizukuProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    suspend fun exec(command: String, maxOutputChars: Int = 32_768): ShellResult =
        withContext(Dispatchers.IO) { execBlocking(command, maxOutputChars) }

    private fun java.io.Reader.readLimited(maxChars: Int): String {
        val builder = StringBuilder()
        val buffer = CharArray(4096)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            val remaining = maxChars - total
            if (remaining <= 0) break
            builder.append(buffer, 0, minOf(read, remaining))
            total += read
        }
        return builder.toString()
    }

    companion object {
        private const val TAG = "ShizukuController"
        private const val REQUEST_CODE = 0xD8B1
    }
}

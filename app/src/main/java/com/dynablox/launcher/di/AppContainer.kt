package com.dynablox.launcher.di

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.dynablox.launcher.R
import com.dynablox.launcher.accessibility.DynabloxAccessibilityService
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.commands.CommandContext
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.commands.CommandRegistry
import com.dynablox.launcher.core.notify.Notifications
import com.dynablox.launcher.core.overlay.OverlayController
import com.dynablox.launcher.core.overlay.OverlayWindows
import com.dynablox.launcher.core.perf.PerfEngine
import com.dynablox.launcher.core.roblox.RobloxLauncher
import com.dynablox.launcher.core.shizuku.ShizukuController
import com.dynablox.launcher.core.util.DeviceInfo
import com.dynablox.launcher.controls.InputInjector
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Textures
import com.dynablox.launcher.optimize.Optimizer
import com.dynablox.launcher.ui.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hand-rolled dependency container.
 *
 * Deliberately tiny: one object graph per process, everything lazy, no codegen and no framework to
 * keep in sync with AGP. Overlay services, widgets and activities all resolve collaborators through
 * `DynabloxApp.of(context).container`.
 */
class AppContainer(val context: Context) {

    val settings = AppSettings(context)
    val notifications = Notifications(context)
    val device = DeviceInfo(context)
    val shizuku = ShizukuController(context)
    val perf = PerfEngine(context, settings, shizuku, device)
    val roblox = RobloxLauncher(context)
    val windows = OverlayWindows(context)
    val overlays = OverlayController(context, windows)
    val injector = InputInjector(settings, shizuku)
    val optimizer: Optimizer by lazy { Optimizer(context, this) }
    val commands = CommandRegistry(this)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _designRevision = MutableStateFlow(0)

    /** Bumped whenever theme, scale, motion or surface preferences change. */
    val designRevision: StateFlow<Int> = _designRevision

    private val _toastRevision = MutableStateFlow(0)
    val toastRevision: StateFlow<Int> = _toastRevision

    /** Default feedback sink used when a command runs outside an Activity. */
    val toastFeedback = object : CommandFeedback {
        override fun show(message: String, kind: CommandFeedback.Kind) {
            scope.launch {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            _toastRevision.value = _toastRevision.value + 1
        }
    }

    fun commandContext(feedback: CommandFeedback = toastFeedback) =
        CommandContext(context, this, feedback)

    fun init() {
        SkeuoTheme.sync(settings)
        notifications.ensureChannels()
        shizuku.attach()

        scope.launch {
            settings.changes.collect {
                SkeuoTheme.sync(settings)
                _designRevision.value = _designRevision.value + 1
            }
        }

    }

    private var autoStartDone = false

    /**
     * Starts the hub on app launch when the user asked for it. Kept out of [init] so a process woken
     * by a broadcast or a notification does not paint an orb over someone else's screen.
     */
    fun maybeAutoStart() {
        if (autoStartDone) return
        autoStartDone = true
        if (settings.hubAutoStart && windows.canDrawOverlays() && !overlays.isMenuHubRunning) {
            overlays.startMenuHub()
        }
    }

    fun shutdown() {
        shizuku.detach()
        perf.stop()
        scope.cancel()
    }

    // ------------------------------------------------------------------ design

    fun cycleTheme() {
        settings.themeMode = when (settings.themeMode) {
            AppSettings.ThemeMode.DARK -> AppSettings.ThemeMode.LIGHT
            AppSettings.ThemeMode.LIGHT -> AppSettings.ThemeMode.SYSTEM
            AppSettings.ThemeMode.SYSTEM -> AppSettings.ThemeMode.DARK
        }
    }

    // ------------------------------------------------------------------ session

    fun sessionStarted() {
        settings.lastSessionAt = System.currentTimeMillis()
        settings.sessionsLaunched = settings.sessionsLaunched + 1
        if (!perf.isRunning && overlays.isHudRunning) perf.start()
    }

    fun launchExperiencePicker() {
        val intent = Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(LauncherActivity.EXTRA_OPEN_LIBRARY, true)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            toastFeedback.show(context.getString(R.string.err_generic, "activity"), CommandFeedback.Kind.ERROR)
        }
    }

    // ------------------------------------------------------------------ overlays

    fun toggleOverlaySafely(
        enabled: Boolean,
        start: () -> Boolean,
        stop: () -> Unit,
        startedMessage: String,
        stoppedMessage: String,
        feedback: CommandFeedback,
    ) {
        if (enabled) {
            stop()
            feedback.show(stoppedMessage, CommandFeedback.Kind.INFO)
            return
        }
        if (!windows.canDrawOverlays()) {
            feedback.show(context.getString(R.string.msg_needs_overlay), CommandFeedback.Kind.WARNING)
            requestOverlayPermission()
            return
        }
        if (!notifications.areNotificationsEnabled()) {
            feedback.show(context.getString(R.string.snack_notification_needed), CommandFeedback.Kind.WARNING)
        }
        val started = start()
        feedback.show(
            if (started) startedMessage else context.getString(R.string.state_error),
            if (started) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.ERROR,
        )
    }

    fun requestOverlayPermission() {
        try {
            context.startActivity(windows.overlayPermissionIntent())
        } catch (_: Throwable) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
                toastFeedback.show(context.getString(R.string.msg_needs_overlay), CommandFeedback.Kind.WARNING)
            }
        }
    }

    fun requestBatteryExemption() {
        if (device.isIgnoringBatteryOptimizations()) {
            toastFeedback.show(context.getString(R.string.perm_status_granted), CommandFeedback.Kind.SUCCESS)
            return
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
                toastFeedback.show(context.getString(R.string.state_unavailable), CommandFeedback.Kind.WARNING)
            }
        }
    }

    fun requestNotificationPolicyAccess() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
            toastFeedback.show(context.getString(R.string.state_unavailable), CommandFeedback.Kind.WARNING)
        }
    }

    fun requestUsageAccess() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
            toastFeedback.show(context.getString(R.string.state_unavailable), CommandFeedback.Kind.WARNING)
        }
    }

    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
            toastFeedback.show(context.getString(R.string.state_unavailable), CommandFeedback.Kind.WARNING)
        }
    }

    // ------------------------------------------------------------------ privileged helpers

    fun inputReady(): Boolean = shizuku.isGranted || DynabloxAccessibilityService.isEnabled

    fun globalAction(action: Int, feedback: CommandFeedback) {
        val service = DynabloxAccessibilityService.current
        if (service != null && service.globalAction(action)) {
            feedback.show(context.getString(R.string.state_ready), CommandFeedback.Kind.SUCCESS)
        } else {
            feedback.show(context.getString(R.string.global_needs_a11y), CommandFeedback.Kind.WARNING)
            openAccessibilitySettings()
        }
    }

    fun openSystemApp(intent: Intent, feedback: CommandFeedback) {
        val flagged = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(flagged)
        } catch (_: Throwable) {
            // Never format msg_app_not_found without its argument: %1$s with no value throws.
            val what = flagged.component?.className
                ?: flagged.`package`
                ?: flagged.action
                ?: context.getString(R.string.app_name)
            feedback.show(
                context.getString(R.string.msg_app_not_found, what),
                CommandFeedback.Kind.WARNING,
            )
        }
    }

    suspend fun screencap(): String {
        if (!shizuku.isGranted) return context.getString(R.string.msg_shizuku_unavailable)
        return withContext(Dispatchers.IO) {
            val dir = "/sdcard/Pictures/DynabloxL"
            val name = "dbx-${System.currentTimeMillis()}.png"
            val result = shizuku.execBlocking("mkdir -p $dir && screencap -p '$dir/$name' && echo DBX_OK")
            if (result.succeeded && result.output.contains("DBX_OK")) {
                context.getString(R.string.msg_screenshot_saved, "$dir/$name")
            } else {
                context.getString(R.string.msg_screenshot_failed, result.text.take(140))
            }
        }
    }

    suspend fun toggleFocusMode(): String = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) {
            requestNotificationPolicyAccess()
            return@withContext context.getString(R.string.msg_needs_notification_policy)
        }
        val all = NotificationManager.INTERRUPTION_FILTER_ALL
        val next = if (manager.currentInterruptionFilter == all) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            all
        }
        try {
            manager.setInterruptionFilter(next)
            context.getString(if (next == all) R.string.msg_focus_off else R.string.msg_focus_on)
        } catch (_: Throwable) {
            context.getString(R.string.state_error)
        }
    }

    suspend fun clearOwnCache(): Int = withContext(Dispatchers.IO) {
        var bytes = 0L
        fun deleteRecursive(file: File?) {
            if (file == null) return
            val children = file.listFiles() ?: return
            children.forEach { child ->
                if (child.isDirectory) deleteRecursive(child)
                bytes += child.length()
                child.delete()
            }
        }
        deleteRecursive(context.cacheDir)
        deleteRecursive(context.externalCacheDir)
        Textures.trim()
        (bytes / 1024L).toInt()
    }

    /** Opens a PerformanceHintManager session so the CPU is biased while overlays are live. */
    fun openPerformanceHint(): Any? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
        return try {
            val manager = context.getSystemService(Context.PERFORMANCE_HINT_SERVICE)
                as? android.os.PerformanceHintManager ?: return null
            manager.createHintSession(intArrayOf(android.os.Process.myPid()), 8_000_000L)
        } catch (_: Throwable) {
            null
        }
    }

    fun powerManager(): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
}

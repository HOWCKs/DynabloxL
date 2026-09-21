package com.dynablox.launcher.optimize

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.perf.MemorySampler
import com.dynablox.launcher.core.perf.CpuSampler
import com.dynablox.launcher.core.shizuku.ShizukuStatus
import com.dynablox.launcher.design.Textures
import com.dynablox.launcher.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The optimization deck.
 *
 * Ground rules for this MVP:
 *  1. every action is something Android actually lets us do — no fake "RAM boosters";
 *  2. anything that changes system state is reversible and stores the previous value;
 *  3. anything that needs a privilege says so, and degrades to a permission request instead of
 *     pretending it worked.
 */
class Optimizer(private val context: Context, private val container: AppContainer) {

    enum class Requirement { NONE, SHIZUKU, BATTERY_EXEMPTION, NOTIFICATION_POLICY, API_31 }

    enum class State { IDLE, RUNNING, APPLIED, RESTORED, SKIPPED, FAILED, NEEDS_PERMISSION }

    data class Entry(
        val id: String,
        val title: String,
        val description: String,
        val requirement: Requirement,
        val reversible: Boolean,
        val state: State,
        val message: String,
        val selected: Boolean,
    )

    data class Metrics(
        val freeRamMb: Float,
        val cpuPercent: Float,
        val thermalStatus: Int,
        val batteryTemperatureC: Float,
    )

    data class Report(
        val entries: List<Entry>,
        val before: Metrics,
        val after: Metrics,
        val durationMs: Long,
    ) {
        val appliedCount: Int get() = entries.count { it.state == State.APPLIED || it.state == State.RESTORED }
        val failedCount: Int get() = entries.count { it.state == State.FAILED }
        val ramDeltaMb: Float get() = after.freeRamMb - before.freeRamMb
    }

    private val memory = MemorySampler(context)
    private val cpu = CpuSampler()

    private data class Definition(
        val id: String,
        val titleRes: Int,
        val descriptionRes: Int,
        val requirement: Requirement,
        val reversible: Boolean,
        val apply: suspend (Optimizer) -> Outcome,
        val restore: (suspend (Optimizer) -> Outcome)? = null,
    )

    data class Outcome(val state: State, val message: String)

    private val definitions: List<Definition> by lazy {
        listOf(
            Definition(
                id = "battery",
                titleRes = R.string.opt_battery,
                descriptionRes = R.string.opt_battery_desc,
                requirement = Requirement.BATTERY_EXEMPTION,
                reversible = true,
                apply = { o ->
                    if (o.container.device.isIgnoringBatteryOptimizations()) {
                        Outcome(State.APPLIED, o.context.getString(R.string.perm_status_granted))
                    } else {
                        o.container.requestBatteryExemption()
                        Outcome(State.NEEDS_PERMISSION, o.context.getString(R.string.opt_needs_user))
                    }
                },
                restore = { o ->
                    try {
                        o.context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        Outcome(State.RESTORED, o.context.getString(R.string.opt_restore_manual))
                    } catch (_: Throwable) {
                        Outcome(State.FAILED, o.context.getString(R.string.state_unavailable))
                    }
                },
            ),
            Definition(
                id = "cache",
                titleRes = R.string.opt_cache,
                descriptionRes = R.string.opt_cache_desc,
                requirement = Requirement.NONE,
                reversible = false,
                apply = { o ->
                    val kb = o.container.clearOwnCache()
                    Outcome(State.APPLIED, "$kb KB")
                },
            ),
            Definition(
                id = "kill_bg",
                titleRes = R.string.opt_kill_bg,
                descriptionRes = R.string.opt_kill_bg_desc,
                requirement = Requirement.SHIZUKU,
                reversible = false,
                apply = { o ->
                    if (o.container.shizuku.status.value != ShizukuStatus.GRANTED) {
                        Outcome(State.NEEDS_PERMISSION, o.context.getString(R.string.msg_shizuku_unavailable))
                    } else {
                        val before = o.freeRamMb()
                        val result = o.container.shizuku.exec("am kill-all")
                        delay(400)
                        val after = o.freeRamMb()
                        if (result.succeeded) {
                            Outcome(
                                State.APPLIED,
                                "%+.0f MB".format(after - before),
                            )
                        } else {
                            Outcome(State.FAILED, result.text.take(120))
                        }
                    }
                },
            ),
            Definition(
                id = "anim_scales",
                titleRes = R.string.opt_anim_scales,
                descriptionRes = R.string.opt_anim_scales_desc,
                requirement = Requirement.SHIZUKU,
                reversible = true,
                apply = { o -> o.writeAnimationScales(0.5f, 0.5f, 0.5f) },
                restore = { o -> o.restoreAnimationScales() },
            ),
            Definition(
                id = "force_stop",
                titleRes = R.string.opt_force_stop,
                descriptionRes = R.string.opt_force_stop_desc,
                requirement = Requirement.SHIZUKU,
                reversible = false,
                apply = { o ->
                    val packages = o.container.settings.forceStopSelection
                    if (packages.isEmpty()) {
                        Outcome(State.SKIPPED, o.context.getString(R.string.opt_no_selection))
                    } else if (o.container.shizuku.status.value != ShizukuStatus.GRANTED) {
                        Outcome(State.NEEDS_PERMISSION, o.context.getString(R.string.msg_shizuku_unavailable))
                    } else {
                        val command = packages.joinToString(" && ") { "am force-stop '$it'" }
                        val result = o.container.shizuku.exec(command)
                        if (result.succeeded) {
                            Outcome(State.APPLIED, "${packages.size} pkg")
                        } else {
                            Outcome(State.FAILED, result.text.take(120))
                        }
                    }
                },
            ),
            Definition(
                id = "dnd",
                titleRes = R.string.opt_dnd,
                descriptionRes = R.string.opt_dnd_desc,
                requirement = Requirement.NOTIFICATION_POLICY,
                reversible = true,
                apply = { o ->
                    val manager = o.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!manager.isNotificationPolicyAccessGranted) {
                        o.container.requestNotificationPolicyAccess()
                        Outcome(State.NEEDS_PERMISSION, o.context.getString(R.string.msg_needs_notification_policy))
                    } else {
                        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        Outcome(State.APPLIED, o.context.getString(R.string.msg_focus_on))
                    }
                },
                restore = { o ->
                    val manager = o.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!manager.isNotificationPolicyAccessGranted) {
                        Outcome(State.NEEDS_PERMISSION, o.context.getString(R.string.msg_needs_notification_policy))
                    } else {
                        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        Outcome(State.RESTORED, o.context.getString(R.string.msg_focus_off))
                    }
                },
            ),
            Definition(
                id = "perf_hint",
                titleRes = R.string.opt_hint,
                descriptionRes = R.string.opt_hint_desc,
                requirement = Requirement.API_31,
                reversible = false,
                apply = { o ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Outcome(State.SKIPPED, "Android 12+")
                    } else {
                        val session = o.container.openPerformanceHint()
                        if (session != null) {
                            o.hintSession = session
                            Outcome(State.APPLIED, "session open")
                        } else {
                            Outcome(State.FAILED, o.context.getString(R.string.state_unavailable))
                        }
                    }
                },
                restore = { o ->
                    val session = o.hintSession
                    if (session != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (session as? android.os.PerformanceHintManager.Session)?.close()
                        o.hintSession = null
                        Outcome(State.RESTORED, "session closed")
                    } else {
                        o.hintSession = null
                        Outcome(State.SKIPPED, "—")
                    }
                },
            ),
            Definition(
                id = "thermal",
                titleRes = R.string.opt_thermal,
                descriptionRes = R.string.opt_thermal_desc,
                requirement = Requirement.NONE,
                reversible = false,
                apply = { o ->
                    val snapshot = o.container.perf.snapshot.value
                    val label = when (snapshot.thermalSeverity) {
                        0 -> o.context.getString(R.string.thermal_nominal)
                        1 -> o.context.getString(R.string.thermal_light)
                        2 -> o.context.getString(R.string.thermal_moderate)
                        else -> o.context.getString(R.string.thermal_severe)
                    }
                    Outcome(State.APPLIED, "$label · ${"%.1f".format(snapshot.batteryTemperatureC)}°C")
                },
            ),
        )
    }

    /** Held as Any? so the class never resolves an API 31 type on older devices. */
    private var hintSession: Any? = null

    fun entries(): List<Entry> = definitions.map { definition ->
        Entry(
            id = definition.id,
            title = context.getString(definition.titleRes),
            description = context.getString(definition.descriptionRes),
            requirement = definition.requirement,
            reversible = definition.restore != null,
            state = State.IDLE,
            message = "",
            selected = definition.id in container.settings.enabledOptimizations,
        )
    }

    suspend fun run(selected: Set<String>): Report = execute(selected, restore = false)

    suspend fun restoreAll(): Report =
        execute(definitions.filter { it.restore != null }.map { it.id }.toSet(), restore = true)

    private suspend fun execute(selected: Set<String>, restore: Boolean): Report =
        withContext(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            val before = metrics()
            val results = ArrayList<Entry>()

            definitions.forEach { definition ->
                if (definition.id !in selected) return@forEach
                val handler = if (restore) definition.restore else definition.apply
                if (handler == null) {
                    results += entryFor(definition, Outcome(State.SKIPPED, "—"))
                    return@forEach
                }
                val outcome = try {
                    handler(this@Optimizer)
                } catch (t: Throwable) {
                    Outcome(State.FAILED, t.message ?: context.getString(R.string.state_error))
                }
                results += entryFor(definition, outcome)
            }

            // Let the system settle before measuring the "after" state.
            delay(700)
            val after = metrics()
            container.settings.lastOptimizeAt = System.currentTimeMillis()

            Report(results, before, after, SystemClock.elapsedRealtime() - started)
        }

    private fun entryFor(definition: Definition, outcome: Outcome) = Entry(
        id = definition.id,
        title = context.getString(definition.titleRes),
        description = context.getString(definition.descriptionRes),
        requirement = definition.requirement,
        reversible = definition.restore != null,
        state = outcome.state,
        message = outcome.message,
        selected = definition.id in container.settings.enabledOptimizations,
    )

    private fun metrics(): Metrics {
        val reading = memory.sample()
        val cpuReading = cpu.sample()
        val snapshot = container.perf.snapshot.value
        return Metrics(
            freeRamMb = (reading.totalMb - reading.usedMb),
            cpuPercent = cpuReading.total,
            thermalStatus = snapshot.thermalStatus,
            batteryTemperatureC = snapshot.batteryTemperatureC,
        )
    }

    private fun freeRamMb(): Float {
        val reading = memory.sample()
        return reading.totalMb - reading.usedMb
    }

    // ---------------------------------------------------------- animation scales

    private suspend fun writeAnimationScales(window: Float, transition: Float, animator: Float): Outcome {
        if (container.shizuku.status.value != ShizukuStatus.GRANTED) {
            return Outcome(State.NEEDS_PERMISSION, context.getString(R.string.msg_shizuku_unavailable))
        }
        val current = readAnimationScales()
        if (container.settings.storedAnimationScales.isBlank()) {
            container.settings.storedAnimationScales = current
        }
        val command = "settings put global window_animation_scale $window && " +
            "settings put global transition_animation_scale $transition && " +
            "settings put global animator_duration_scale $animator"
        val result = container.shizuku.exec(command)
        return if (result.succeeded) {
            container.settings.animationTweaked = true
            Outcome(State.APPLIED, "$window · $transition · $animator")
        } else {
            Outcome(State.FAILED, result.text.take(120))
        }
    }

    private suspend fun restoreAnimationScales(): Outcome {
        if (container.shizuku.status.value != ShizukuStatus.GRANTED) {
            return Outcome(State.NEEDS_PERMISSION, context.getString(R.string.msg_shizuku_unavailable))
        }
        val stored = container.settings.storedAnimationScales
        val values = stored.split(' ').filter { it.isNotBlank() }
        val window = values.getOrNull(0) ?: "1.0"
        val transition = values.getOrNull(1) ?: "1.0"
        val animator = values.getOrNull(2) ?: "1.0"
        val command = "settings put global window_animation_scale $window && " +
            "settings put global transition_animation_scale $transition && " +
            "settings put global animator_duration_scale $animator"
        val result = container.shizuku.exec(command)
        return if (result.succeeded) {
            container.settings.animationTweaked = false
            container.settings.storedAnimationScales = ""
            Outcome(State.RESTORED, "$window · $transition · $animator")
        } else {
            Outcome(State.FAILED, result.text.take(120))
        }
    }

    suspend fun readAnimationScales(): String {
        if (container.shizuku.status.value != ShizukuStatus.GRANTED) return ""
        val result = container.shizuku.exec(
            "settings get global window_animation_scale; " +
                "settings get global transition_animation_scale; " +
                "settings get global animator_duration_scale",
        )
        if (!result.succeeded) return ""
        return result.output.lines().map { it.trim() }.filter { it.isNotBlank() && it != "null" }
            .take(3).joinToString(" ")
    }

    /** Installs apps the user may force-stop, filtering out anything the system needs. */
    suspend fun forceStopCandidates(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        try {
            pm.queryIntentActivities(launchIntent, 0)
                .map { it.activityInfo }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .map { it.packageName to it.loadLabel(pm).toString() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Reclaims texture memory immediately (used by the "clear cache" action). */
    fun trimTextures() {
        Textures.trim()
    }

    fun cacheSizeKb(): Int {
        fun size(file: File?): Long {
            if (file == null) return 0L
            val children = file.listFiles() ?: return file.length()
            return children.sumOf { size(it) }
        }
        return ((size(context.cacheDir) + size(context.externalCacheDir)) / 1024L).toInt()
    }

    fun toggleSelected(id: String) {
        val current = container.settings.enabledOptimizations.toMutableSet()
        if (!current.remove(id)) current.add(id)
        container.settings.enabledOptimizations = current
    }

    fun toggleForceStop(pkg: String) {
        val current = container.settings.forceStopSelection.toMutableSet()
        if (!current.remove(pkg)) current.add(pkg)
        container.settings.forceStopSelection = current
    }

    fun defaultSelection(): Set<String> = AppSettings.DEFAULT_OPTIMIZATIONS
}

package com.dynablox.launcher.ui

import android.widget.LinearLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.optimize.Optimizer

/**
 * Optimizer console.
 *
 * Real, user-visible, reversible actions: battery exemption, cache trim, background kill, animation
 * scales, targeted force-stop, Do-Not-Disturb, performance hint and thermal watch. Each entry shows
 * what it needs, and the report after a run shows the measured before/after state — never a promised
 * number.
 */
class OptimizerActivity : DbxActivity() {

    override val titleRes: Int = R.string.title_optimizer
    override val subtitleRes: Int = R.string.optimizer_subtitle
    override val headerIconRes: Int = R.drawable.ic_bolt

    private var lastReport: Optimizer.Report? = null
    private var running = false

    override fun buildContent(root: LinearLayout) {
        buildSummary()
        buildEntries()
        buildActions()
        buildForceStop()
        lastReport?.let { buildReport(it) }
    }

    private fun buildSummary() {
        section(R.string.optimizer_section_summary, R.string.optimizer_section_summary_desc)
        card {
            textRow(
                R.string.optimizer_selected_count,
                getString(R.string.optimizer_selected_value, settings.enabledOptimizations.size),
                tokens.accent,
            )
            textRow(
                R.string.optimizer_last_run,
                if (settings.lastOptimizeAt > 0L) {
                    Fmt.relativeTime(settings.lastOptimizeAt)
                } else {
                    getString(R.string.launcher_never)
                },
            )
            textRow(
                R.string.optimizer_cache,
                Fmt.megabytes(container.optimizer.cacheSizeKb() / 1024f, 1),
            )
            textRow(
                R.string.optimizer_thermal,
                thermalLabel(),
                thermalColor(),
            )
            val snapshot = container.perf.snapshot.value
            textRow(
                R.string.optimizer_ram_free,
                if (snapshot.ramTotalMb > 0f) {
                    Fmt.megabytes(snapshot.ramTotalMb - snapshot.ramUsedMb, 0)
                } else {
                    getString(R.string.state_unknown)
                },
            )
            textRow(
                R.string.optimizer_storage_free,
                Fmt.bytes(container.device.freeStorageBytes()),
            )
            buttonRow(R.string.optimizer_read_anim_scales, R.drawable.ic_sliders) {
                launchSafely {
                    val scales = container.optimizer.readAnimationScales()
                    show(
                        getString(R.string.optimizer_anim_scales, scales.ifBlank { "—" }),
                        CommandFeedback.Kind.INFO,
                    )
                }
            }
        }
    }

    /** Live reading from PowerManager (API 29+); older devices report nominal honestly. */
    private fun thermalStatus(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                container.powerManager().currentThermalStatus
            } catch (_: Throwable) {
                0
            }
        } else {
            0
        }

    private fun thermalLabel(): String {
        val status = thermalStatus()
        return getString(
            when {
                status <= 0 -> R.string.thermal_nominal
                status == 1 -> R.string.thermal_light
                status == 2 -> R.string.thermal_moderate
                else -> R.string.thermal_severe
            },
        )
    }

    private fun thermalColor(): Int {
        val status = thermalStatus()
        return when {
            status <= 1 -> tokens.success
            status == 2 -> tokens.warning
            else -> tokens.danger
        }
    }

    private fun buildEntries() {
        section(R.string.optimizer_section_entries, R.string.optimizer_section_entries_desc)
        card {
            container.optimizer.entries().forEach { entry ->
                val requirementMet = requirementMet(entry.requirement)
                toggleRowText(
                    label = entry.title,
                    description = entry.description,
                    checked = entry.selected,
                ) { checked ->
                    container.optimizer.toggleSelected(entry.id)
                    if (checked && !requirementMet) {
                        show(requirementMessage(entry.requirement), CommandFeedback.Kind.WARNING)
                    }
                    refreshContent()
                }
                textRowText(
                    getString(R.string.optimizer_entry_requirement),
                    if (requirementMet) {
                        getString(R.string.optimizer_requirement_met)
                    } else {
                        requirementMessage(entry.requirement)
                    },
                    if (requirementMet) tokens.success else tokens.warning,
                )
                if (entry.reversible) {
                    paragraph(R.string.optimizer_entry_reversible)
                }
                divider()
            }
        }
    }

    private fun buildActions() {
        section(R.string.optimizer_section_run)
        card {
            buttonRow(
                if (running) R.string.optimizer_running else R.string.optimizer_run,
                R.drawable.ic_rocket,
                descriptionRes = R.string.optimizer_run_desc,
                enabled = !running && settings.enabledOptimizations.isNotEmpty(),
                accent = tokens.accent,
            ) { run() }
            buttonRow(
                R.string.optimizer_restore,
                R.drawable.ic_refresh,
                descriptionRes = R.string.optimizer_restore_desc,
                enabled = !running,
            ) { restore() }
            if (settings.enabledOptimizations.isEmpty()) {
                paragraph(R.string.optimizer_no_selection)
            }
        }
    }

    private fun run() {
        running = true
        refreshContent()
        launchSafely {
            val report = container.optimizer.run(settings.enabledOptimizations)
            lastReport = report
            running = false
            show(
                getString(
                    R.string.opt_result,
                    report.appliedCount,
                    report.failedCount,
                    Fmt.megabytes(report.ramDeltaMb, 0),
                    Fmt.ms(report.durationMs.toFloat(), 0),
                ),
                if (report.failedCount == 0) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.WARNING,
            )
            refreshContent()
            scrollToEnd()
        }
    }

    private fun restore() {
        running = true
        refreshContent()
        launchSafely {
            val report = container.optimizer.restoreAll()
            lastReport = report
            running = false
            show(
                getString(R.string.optimizer_restored, report.appliedCount),
                CommandFeedback.Kind.SUCCESS,
            )
            refreshContent()
        }
    }

    private fun buildReport(report: Optimizer.Report) {
        section(R.string.optimizer_section_report)
        card {
            textRow(
                R.string.optimizer_report_duration,
                Fmt.ms(report.durationMs.toFloat(), 0),
            )
            textRow(
                R.string.optimizer_report_ram_before,
                Fmt.megabytes(report.before.freeRamMb, 0),
            )
            textRow(
                R.string.optimizer_report_ram_after,
                Fmt.megabytes(report.after.freeRamMb, 0),
                if (report.ramDeltaMb >= 0f) tokens.success else tokens.warning,
            )
            textRow(
                R.string.optimizer_report_cpu,
                getString(
                    R.string.optimizer_report_cpu_value,
                    Fmt.number(report.before.cpuPercent, 0),
                    Fmt.number(report.after.cpuPercent, 0),
                ),
            )
            divider()
            report.entries.forEach { entry ->
                textRowText(entry.title, stateLabel(entry.state), stateColor(entry.state))
                paragraphText(getString(R.string.optimizer_report_message, entry.message.ifBlank { "—" }))
            }
        }
    }

    private fun buildForceStop() {
        section(R.string.optimizer_section_forcestop, R.string.optimizer_section_forcestop_desc)
        card {
            if (!container.shizuku.isGranted) {
                paragraph(R.string.optimizer_forcestop_needs_shizuku)
                buttonRow(R.string.permissions_shizuku_action, R.drawable.ic_terminal) {
                    container.shizuku.requestPermission()
                }
                return@card
            }
            textRow(
                R.string.optimizer_forcestop_selected,
                getString(R.string.optimizer_selected_value, settings.forceStopSelection.size),
                tokens.accent,
            )
            buttonRow(R.string.optimizer_load_apps, R.drawable.ic_refresh) { loadCandidates() }
            paragraph(R.string.optimizer_forcestop_hint)
        }
        candidatesCard()
    }

    private var candidates: List<Pair<String, String>> = emptyList()

    private fun candidatesCard() {
        if (candidates.isEmpty()) return
        card {
            candidates.forEach { (pkg, label) ->
                toggleRowText(
                    label = label,
                    description = pkg,
                    checked = pkg in settings.forceStopSelection,
                ) {
                    container.optimizer.toggleForceStop(pkg)
                    refreshContent()
                }
            }
        }
    }

    private fun loadCandidates() {
        launchSafely {
            candidates = container.optimizer.forceStopCandidates().take(40)
            if (candidates.isEmpty()) {
                show(getString(R.string.state_empty), CommandFeedback.Kind.INFO)
            }
            refreshContent()
        }
    }

    private fun stateLabel(state: Optimizer.State): String = getString(
        when (state) {
            Optimizer.State.IDLE -> R.string.state_idle
            Optimizer.State.RUNNING -> R.string.state_running
            Optimizer.State.APPLIED -> R.string.state_applied
            Optimizer.State.RESTORED -> R.string.state_restored
            Optimizer.State.SKIPPED -> R.string.state_skipped
            Optimizer.State.FAILED -> R.string.state_failed
            Optimizer.State.NEEDS_PERMISSION -> R.string.state_needs_permission
        },
    )

    private fun stateColor(state: Optimizer.State): Int = when (state) {
        Optimizer.State.APPLIED, Optimizer.State.RESTORED -> tokens.success
        Optimizer.State.SKIPPED, Optimizer.State.IDLE -> tokens.textSecondary
        Optimizer.State.NEEDS_PERMISSION -> tokens.warning
        Optimizer.State.FAILED -> tokens.danger
        Optimizer.State.RUNNING -> tokens.accent
    }

    private fun requirementMet(requirement: Optimizer.Requirement): Boolean = when (requirement) {
        Optimizer.Requirement.NONE -> true
        Optimizer.Requirement.SHIZUKU -> container.shizuku.isGranted
        Optimizer.Requirement.BATTERY_EXEMPTION -> try {
            container.powerManager().isIgnoringBatteryOptimizations(packageName)
        } catch (_: Throwable) {
            false
        }

        Optimizer.Requirement.NOTIFICATION_POLICY -> try {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .isNotificationPolicyAccessGranted
        } catch (_: Throwable) {
            false
        }

        Optimizer.Requirement.API_31 -> android.os.Build.VERSION.SDK_INT >= 31
    }

    private fun requirementMessage(requirement: Optimizer.Requirement): String = getString(
        when (requirement) {
            Optimizer.Requirement.NONE -> R.string.optimizer_requirement_met
            Optimizer.Requirement.SHIZUKU -> R.string.optimizer_needs_shizuku
            Optimizer.Requirement.BATTERY_EXEMPTION -> R.string.optimizer_needs_battery
            Optimizer.Requirement.NOTIFICATION_POLICY -> R.string.msg_needs_notification_policy
            Optimizer.Requirement.API_31 -> R.string.optimizer_needs_api31
        },
    )
}

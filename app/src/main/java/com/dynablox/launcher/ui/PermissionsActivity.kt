package com.dynablox.launcher.ui

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.dynablox.launcher.R
import com.dynablox.launcher.accessibility.DynabloxAccessibilityService
import com.dynablox.launcher.core.commands.CommandFeedback

/**
 * Permission desk.
 *
 * Nothing here is decorative: each row reads the *real* system state (overlay, notifications,
 * battery optimisation, Shizuku, accessibility, usage access, Do-Not-Disturb policy) and its button
 * opens exactly the screen that grants it, or explains why the feature is degraded without it.
 */
class PermissionsActivity : DbxActivity() {

    override val titleRes: Int = R.string.title_permissions
    override val subtitleRes: Int = R.string.permissions_subtitle
    override val headerIconRes: Int = R.drawable.ic_shield

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            show(
                getString(
                    if (granted) R.string.permissions_granted else R.string.permissions_denied,
                    getString(R.string.permissions_notifications),
                ),
                if (granted) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.WARNING,
            )
            refreshContent()
        }

    override fun onResume() {
        super.onResume()
        container.shizuku.refresh()
    }

    override fun buildContent(root: LinearLayout) {
        section(R.string.permissions_section_required, R.string.permissions_section_required_desc)
        card {
            permissionRow(
                labelRes = R.string.permissions_overlay,
                descriptionRes = R.string.permissions_overlay_desc,
                granted = container.windows.canDrawOverlays(),
                actionRes = R.string.permissions_open_overlay,
            ) { container.requestOverlayPermission() }

            permissionRow(
                labelRes = R.string.permissions_notifications,
                descriptionRes = R.string.permissions_notifications_desc,
                granted = container.notifications.areNotificationsEnabled(),
                actionRes = R.string.permissions_request,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    container.openAccessibilitySettings()
                }
            }
        }

        section(R.string.permissions_section_input, R.string.permissions_section_input_desc)
        card {
            permissionRow(
                labelRes = R.string.permissions_shizuku,
                descriptionRes = R.string.permissions_shizuku_desc,
                granted = container.shizuku.isGranted,
                actionRes = R.string.permissions_shizuku_action,
                stateText = shizukuStateText(),
            ) { container.shizuku.requestPermission() }

            permissionRow(
                labelRes = R.string.permissions_accessibility,
                descriptionRes = R.string.permissions_accessibility_desc,
                granted = DynabloxAccessibilityService.isEnabled,
                actionRes = R.string.permissions_open_accessibility,
            ) { container.openAccessibilitySettings() }

            textRow(
                R.string.permissions_input_backend,
                getString(
                    when {
                        container.shizuku.isGranted -> R.string.permissions_backend_shizuku
                        DynabloxAccessibilityService.isEnabled -> R.string.permissions_backend_accessibility
                        else -> R.string.permissions_backend_none
                    },
                ),
                if (container.inputReady()) tokens.success else tokens.danger,
            )
            paragraph(R.string.permissions_input_backend_desc)
        }

        section(R.string.permissions_section_optimizer, R.string.permissions_section_optimizer_desc)
        card {
            permissionRow(
                labelRes = R.string.permissions_battery,
                descriptionRes = R.string.permissions_battery_desc,
                granted = ignoringBatteryOptimizations(),
                actionRes = R.string.permissions_request,
            ) { container.requestBatteryExemption() }

            permissionRow(
                labelRes = R.string.permissions_dnd,
                descriptionRes = R.string.permissions_dnd_desc,
                granted = notificationPolicyGranted(),
                actionRes = R.string.permissions_request,
            ) { container.requestNotificationPolicyAccess() }

            permissionRow(
                labelRes = R.string.permissions_usage,
                descriptionRes = R.string.permissions_usage_desc,
                granted = usageStatsGranted(),
                actionRes = R.string.permissions_request,
            ) { container.requestUsageAccess() }
        }

        section(R.string.permissions_section_health)
        card {
            textRow(
                R.string.permissions_ready,
                getString(if (readyForEverything()) R.string.state_ready else R.string.state_degraded),
                if (readyForEverything()) tokens.success else tokens.warning,
            )
            paragraph(R.string.permissions_health_desc)
            buttonRow(R.string.permissions_recheck, R.drawable.ic_refresh) {
                container.shizuku.refresh()
                refreshContent()
                show(getString(R.string.permissions_rechecked), CommandFeedback.Kind.INFO)
            }
        }
    }

    private fun LinearLayout.permissionRow(
        labelRes: Int,
        descriptionRes: Int,
        granted: Boolean,
        actionRes: Int,
        stateText: String? = null,
        onAction: () -> Unit,
    ) {
        ledRow(
            getString(labelRes),
            granted,
            if (granted) tokens.success else tokens.warning,
        )
        textRow(
            labelRes,
            stateText ?: getString(if (granted) R.string.state_granted else R.string.state_missing),
            if (granted) tokens.success else tokens.warning,
        )
        paragraph(descriptionRes)
        if (!granted) {
            buttonRow(actionRes, R.drawable.ic_chevron) {
                onAction()
                postDelayed({ refreshContent() }, 600L)
            }
        }
        divider()
    }

    private fun shizukuStateText(): String {
        val backend = container.shizuku.backend.value
        return if (container.shizuku.isGranted && backend.isNotBlank()) {
            getString(R.string.permissions_shizuku_state, backend)
        } else {
            getString(if (container.shizuku.isGranted) R.string.state_granted else R.string.state_missing)
        }
    }

    private fun ignoringBatteryOptimizations(): Boolean = try {
        container.powerManager().isIgnoringBatteryOptimizations(packageName)
    } catch (_: Throwable) {
        false
    }

    private fun notificationPolicyGranted(): Boolean = try {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.isNotificationPolicyAccessGranted
    } catch (_: Throwable) {
        false
    }

    private fun usageStatsGranted(): Boolean = try {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        checkSelfPermissionCompat()
    }

    private fun checkSelfPermissionCompat(): Boolean = try {
        checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS") ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun readyForEverything(): Boolean = container.windows.canDrawOverlays() &&
        container.notifications.areNotificationsEnabled() &&
        container.inputReady()
}

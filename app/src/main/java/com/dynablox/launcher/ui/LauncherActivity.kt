package com.dynablox.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import com.dynablox.launcher.R
import com.dynablox.launcher.accessibility.DynabloxAccessibilityService
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.roblox.LaunchOutcome
import com.dynablox.launcher.core.roblox.RobloxLauncher
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.design.widgets.OrbView

/**
 * Home screen of the launcher.
 *
 * Real work only: device + permission state, Roblox detection and deep-link launch into a curated
 * experience (or any place ID the user types), live toggles for the four overlays, the installed-app
 * grid and the doors into Settings, Shader Lab, Controls, Optimizer and Permissions.
 */
class LauncherActivity : DbxActivity() {

    override val titleRes: Int = R.string.app_name
    override val subtitleRes: Int = R.string.app_tagline
    override val headerIconRes: Int = R.drawable.ic_hub

    private var libraryCard: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.maybeAutoStart()
        routeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshContent()
        routeIntent(intent)
    }

    private fun routeIntent(intent: Intent?) {
        when {
            intent == null -> Unit
            intent.getBooleanExtra(EXTRA_OPEN_CONTROLS, false) -> open(ControlsActivity::class.java)
            intent.getBooleanExtra(EXTRA_OPEN_FX, false) -> container.overlays.toggleFx()
            intent.getBooleanExtra(EXTRA_OPEN_SHADER, false) -> open(ShaderLabActivity::class.java)
            intent.getBooleanExtra(EXTRA_OPEN_OPTIMIZER, false) -> open(OptimizerActivity::class.java)
            intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false) -> libraryCard?.let {
                it.post { scrollToEnd() }
            }
        }
    }

    override fun buildContent(root: LinearLayout) {
        buildConsole(root)
        buildRoblox(root)
        buildOverlays(root)
        buildApps(root)
        buildNavigation(root)
    }

    // ------------------------------------------------------------------ console

    private fun buildConsole(root: LinearLayout) {
        section(R.string.launcher_section_console, R.string.launcher_section_console_desc)
        val device = container.device
        card {
            val orbRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val orb = OrbView(context).apply {
                expanded = true
                iconRes = R.drawable.ic_hub
                contentDescription = getString(R.string.launcher_orb_preview)
                setOnClickListener { container.overlays.toggleMenuHub() }
            }
            orbRow.addView(orb, LinearLayout.LayoutParams(dp(72f).toInt(), dp(72f).toInt()))
            val orbText = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val start = dp(14f).toInt()
            orbText.setPadding(start, 0, 0, 0)
            orbText.addView(
                TextView(context, null, 0, R.style.Dbx_Text_Title).apply {
                    text = getString(R.string.launcher_orb_title)
                },
            )
            orbText.addView(
                TextView(context, null, 0, R.style.Dbx_Text_Caption).apply {
                    text = getString(R.string.launcher_orb_hint)
                },
            )
            orbRow.addView(
                orbText,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(orbRow, wrap())

            divider()
            textRow(R.string.launcher_device, "${device.manufacturer} ${device.model}")
            textRow(R.string.launcher_android, "API ${device.sdk} · ${device.androidRelease}")
            textRow(
                R.string.launcher_hardware,
                getString(
                    R.string.launcher_hardware_value,
                    device.cores,
                    Fmt.bytes(device.totalRamBytes),
                    Fmt.number(device.maxRefreshRate, 0),
                ),
            )
            textRow(
                R.string.launcher_sessions,
                getString(
                    R.string.launcher_sessions_value,
                    settings.sessionsLaunched,
                    if (settings.lastSessionAt > 0L) {
                        Fmt.relativeTime(settings.lastSessionAt)
                    } else {
                        getString(R.string.launcher_never)
                    },
                ),
            )
            divider()
            ledRow(
                getString(R.string.launcher_led_overlay),
                container.windows.canDrawOverlays(),
                if (container.windows.canDrawOverlays()) tokens.success else tokens.warning,
            )
            ledRow(
                getString(R.string.launcher_led_shizuku),
                container.shizuku.isGranted,
                if (container.shizuku.isGranted) tokens.accent else tokens.warning,
            )
            ledRow(
                getString(R.string.launcher_led_accessibility),
                DynabloxAccessibilityService.isEnabled,
                if (DynabloxAccessibilityService.isEnabled) tokens.accent else tokens.ledOff,
            )
            ledRow(
                getString(R.string.launcher_led_input),
                container.inputReady(),
                if (container.inputReady()) tokens.success else tokens.danger,
            )
            buttonRow(R.string.launcher_permissions, R.drawable.ic_shield) {
                open(PermissionsActivity::class.java)
            }
        }
    }

    // ------------------------------------------------------------------ roblox

    private fun buildRoblox(root: LinearLayout) {
        section(R.string.launcher_section_roblox, R.string.launcher_section_roblox_desc)
        val installed = container.roblox.isInstalled()
        card {
            textRow(
                R.string.launcher_roblox_state,
                if (installed) {
                    container.roblox.versionName() ?: getString(R.string.launcher_installed)
                } else {
                    getString(R.string.launcher_not_installed)
                },
                if (installed) tokens.success else tokens.warning,
            )
            buttonRow(
                R.string.launcher_play,
                R.drawable.ic_rocket,
                descriptionRes = R.string.launcher_play_desc,
                enabled = installed,
                accent = tokens.accent,
            ) { launchExperience(currentPlaceId()) }

            if (!installed) {
                buttonRow(R.string.launcher_install, R.drawable.ic_download) {
                    if (!container.roblox.openPlayStore()) {
                        show(getString(R.string.err_generic, "play store"), CommandFeedback.Kind.ERROR)
                    }
                }
            }

            divider()
            paragraph(R.string.launcher_library_desc)
            RobloxLauncher.EXPERIENCES.forEach { experience ->
                val selected = settings.selectedPlaceId == experience.placeId
                actionRow(
                    label = experience.name,
                    subLabel = getString(
                        R.string.launcher_experience_meta,
                        experience.genre,
                        experience.placeId,
                    ),
                    iconRes = experience.iconRes,
                    accent = if (selected) tokens.accent else null,
                ) {
                    settings.selectedPlaceId = experience.placeId
                    if (installed) {
                        launchExperience(experience.placeId)
                    } else {
                        show(
                            getString(R.string.launcher_experience_selected, experience.name),
                            CommandFeedback.Kind.INFO,
                        )
                        refreshContent()
                    }
                }
            }

            divider()
            addView(
                TextView(context, null, 0, R.style.Dbx_Text_Body).apply {
                    text = getString(R.string.launcher_custom_place)
                },
                wrap(top = dp(8f)),
            )
            val field = AppCompatEditText(context).apply {
                hint = getString(R.string.launcher_custom_place_hint)
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.customPlaceId)
                setSingleLine(true)
                textSize = 14f
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textFaint)
                val pad = dp(12f).toInt()
                setPadding(pad, pad, pad, pad)
                background = null
            }
            addView(field, wrap(top = dp(6f)))
            buttonRow(R.string.launcher_custom_go, R.drawable.ic_link, enabled = installed) {
                val typed = field.text?.toString()?.trim().orEmpty()
                settings.customPlaceId = typed
                if (typed.isEmpty()) {
                    show(getString(R.string.launcher_custom_empty), CommandFeedback.Kind.WARNING)
                } else {
                    launchExperience(typed)
                }
            }
        }.also { libraryCard = it }
    }

    private fun currentPlaceId(): String? {
        val custom = settings.customPlaceId.trim()
        if (custom.isNotEmpty()) return custom
        val selected = settings.selectedPlaceId.trim()
        return selected.ifEmpty { null }
    }

    private fun launchExperience(placeId: String?) {
        val outcome = container.roblox.launch(placeId)
        val (message, kind) = when (outcome) {
            LaunchOutcome.LAUNCHED ->
                getString(R.string.launcher_launched) to CommandFeedback.Kind.SUCCESS

            LaunchOutcome.DEEP_LINK_FAILED_FALLBACK ->
                getString(R.string.launcher_launched_fallback) to CommandFeedback.Kind.WARNING

            LaunchOutcome.NOT_INSTALLED ->
                getString(R.string.launcher_not_installed) to CommandFeedback.Kind.ERROR

            LaunchOutcome.FAILED ->
                getString(R.string.launcher_launch_failed) to CommandFeedback.Kind.ERROR
        }
        show(message, kind)
        if (outcome == LaunchOutcome.LAUNCHED || outcome == LaunchOutcome.DEEP_LINK_FAILED_FALLBACK) {
            container.sessionStarted()
            if (settings.hudAutoStartWithSession) container.overlays.startHud()
            moveTaskToBack(true)
        }
    }

    // ------------------------------------------------------------------ overlays

    private fun buildOverlays(root: LinearLayout) {
        section(R.string.launcher_section_overlays, R.string.launcher_section_overlays_desc)
        card {
            overlayToggle(
                labelRes = R.string.launcher_overlay_hub,
                descriptionRes = R.string.launcher_overlay_hub_desc,
                running = container.overlays.isMenuHubRunning,
                onToggle = { container.overlays.toggleMenuHub() },
            )
            overlayToggle(
                labelRes = R.string.launcher_overlay_hud,
                descriptionRes = R.string.launcher_overlay_hud_desc,
                running = container.overlays.isHudRunning,
                onToggle = { container.overlays.toggleHud() },
            )
            overlayToggle(
                labelRes = R.string.launcher_overlay_controls,
                descriptionRes = R.string.launcher_overlay_controls_desc,
                running = container.overlays.isControlsRunning,
                onToggle = { container.overlays.toggleControls() },
            )
            overlayToggle(
                labelRes = R.string.launcher_overlay_fx,
                descriptionRes = R.string.launcher_overlay_fx_desc,
                running = container.overlays.isFxRunning,
                onToggle = { container.overlays.toggleFx() },
            )
            divider()
            textRow(
                R.string.launcher_overlay_permission,
                getString(
                    if (container.windows.canDrawOverlays()) {
                        R.string.launcher_granted
                    } else {
                        R.string.launcher_missing
                    },
                ),
                if (container.windows.canDrawOverlays()) tokens.success else tokens.danger,
            )
            if (!container.windows.canDrawOverlays()) {
                buttonRow(R.string.launcher_grant_overlay, R.drawable.ic_shield) {
                    container.requestOverlayPermission()
                }
            }
            buttonRow(R.string.launcher_stop_all, R.drawable.ic_stop) {
                container.overlays.stopEverything()
                show(getString(R.string.launcher_stopped_all), CommandFeedback.Kind.SUCCESS)
                refreshContent()
            }
        }
    }

    private fun LinearLayout.overlayToggle(
        labelRes: Int,
        descriptionRes: Int,
        running: Boolean,
        onToggle: () -> Unit,
    ) {
        toggleRow(
            labelRes = labelRes,
            descriptionRes = descriptionRes,
            checked = running,
            enabled = container.windows.canDrawOverlays(),
        ) { checked ->
            if (!container.windows.canDrawOverlays()) {
                container.requestOverlayPermission()
                return@toggleRow
            }
            onToggle()
            postDelayed({ refreshContent() }, 400L)
        }
    }

    // ------------------------------------------------------------------ apps

    private fun buildApps(root: LinearLayout) {
        section(R.string.launcher_section_apps, R.string.launcher_section_apps_desc)
        val apps = container.roblox.launchableApps(limit = 18)
            .filter { it.packageName != packageName }
        card {
            if (apps.isEmpty()) {
                paragraph(R.string.state_empty)
            } else {
                apps.forEach { app ->
                    actionRow(
                        label = app.label,
                        subLabel = app.packageName,
                        icon = app.icon,
                    ) {
                        if (!container.roblox.launchPackage(app.packageName)) {
                            show(
                                getString(R.string.msg_app_not_found, app.packageName),
                                CommandFeedback.Kind.ERROR,
                            )
                        } else {
                            moveTaskToBack(true)
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ navigation

    private fun buildNavigation(root: LinearLayout) {
        section(R.string.launcher_section_tools)
        card {
            buttonRow(R.string.title_settings, R.drawable.ic_settings) {
                open(SettingsActivity::class.java)
            }
            buttonRow(R.string.title_shader_lab, R.drawable.ic_shader) {
                open(ShaderLabActivity::class.java)
            }
            buttonRow(R.string.title_controls, R.drawable.ic_gamepad) {
                open(ControlsActivity::class.java)
            }
            buttonRow(R.string.title_optimizer, R.drawable.ic_bolt) {
                open(OptimizerActivity::class.java)
            }
            buttonRow(R.string.title_permissions, R.drawable.ic_shield) {
                open(PermissionsActivity::class.java)
            }
            divider()
            buttonRow(R.string.launcher_theme, R.drawable.ic_palette) {
                container.cycleTheme()
            }
        }
        addFooter()
    }

    private fun addFooter() {
        contentRoot.addView(
            TextView(this, null, 0, R.style.Dbx_Text_Caption).apply {
                text = getString(R.string.launcher_footer, container.device.model)
                gravity = Gravity.CENTER
                alpha = 0.7f
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(20f).toInt() },
        )
    }

    private fun wrap(top: Float = 0f) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = top.toInt() }

    companion object {
        const val EXTRA_OPEN_LIBRARY = "com.dynablox.launcher.OPEN_LIBRARY"
        const val EXTRA_OPEN_CONTROLS = "com.dynablox.launcher.OPEN_CONTROLS"
        const val EXTRA_OPEN_FX = "com.dynablox.launcher.OPEN_FX"
        const val EXTRA_OPEN_SHADER = "com.dynablox.launcher.OPEN_SHADER"
        const val EXTRA_OPEN_OPTIMIZER = "com.dynablox.launcher.OPEN_OPTIMIZER"
    }
}

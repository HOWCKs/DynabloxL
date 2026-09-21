package com.dynablox.launcher.ui

import android.widget.LinearLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.controls.ControlPresets
import com.dynablox.launcher.controls.InputInjector
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.fx.FxPresets

/**
 * Every knob the design system and the overlays expose, in one place.
 *
 * Changes are written straight to [AppSettings]; the overlays observe `settings.changes` and
 * re-tint / re-layout live, and this screen recreates itself when a change affects the theme or the
 * interface scale.
 */
class SettingsActivity : DbxActivity() {

    override val titleRes: Int = R.string.title_settings
    override val subtitleRes: Int = R.string.settings_subtitle
    override val headerIconRes: Int = R.drawable.ic_settings

    override fun buildContent(root: LinearLayout) {
        buildAppearance()
        buildOrb()
        buildHud()
        buildFx()
        buildControls()
        buildData()
    }

    // ------------------------------------------------------------------ appearance

    private fun buildAppearance() {
        section(R.string.settings_section_appearance, R.string.settings_section_appearance_desc)
        card {
            segmentedRow(
                R.string.settings_theme,
                listOf(
                    SegmentOption(getString(R.string.settings_theme_dark), { settings.themeMode == AppSettings.ThemeMode.DARK }) {
                        settings.themeMode = AppSettings.ThemeMode.DARK
                    },
                    SegmentOption(getString(R.string.settings_theme_light), { settings.themeMode == AppSettings.ThemeMode.LIGHT }) {
                        settings.themeMode = AppSettings.ThemeMode.LIGHT
                    },
                    SegmentOption(getString(R.string.settings_theme_system), { settings.themeMode == AppSettings.ThemeMode.SYSTEM }) {
                        settings.themeMode = AppSettings.ThemeMode.SYSTEM
                    },
                ),
            )
            faderRow(
                labelRes = R.string.settings_density,
                min = 85f, max = 140f,
                value = settings.densityScale * 100f,
                unit = "%",
            ) { settings.densityScale = (it / 100f).coerceIn(0.85f, 1.4f) }
            faderRow(
                labelRes = R.string.settings_shadow,
                min = 0f, max = 100f,
                value = settings.shadowIntensity * 100f,
                unit = "%",
            ) { settings.shadowIntensity = (it / 100f).coerceIn(0f, 1f) }
            segmentedRow(
                R.string.settings_animation_style,
                listOf(
                    SegmentOption(getString(R.string.settings_anim_precise), { settings.animationStyle == AppSettings.AnimationStyle.PRECISE }) {
                        settings.animationStyle = AppSettings.AnimationStyle.PRECISE
                    },
                    SegmentOption(getString(R.string.settings_anim_spring), { settings.animationStyle == AppSettings.AnimationStyle.SPRING }) {
                        settings.animationStyle = AppSettings.AnimationStyle.SPRING
                    },
                    SegmentOption(getString(R.string.settings_anim_instant), { settings.animationStyle == AppSettings.AnimationStyle.INSTANT }) {
                        settings.animationStyle = AppSettings.AnimationStyle.INSTANT
                    },
                ),
            )
            toggleRow(
                R.string.settings_reduce_motion, R.string.settings_reduce_motion_desc,
                settings.reduceMotion,
            ) { settings.reduceMotion = it }
            toggleRow(
                R.string.settings_translucent, R.string.settings_translucent_desc,
                settings.translucentSurfaces,
            ) { settings.translucentSurfaces = it }
        }
    }

    // ------------------------------------------------------------------ orb

    private fun buildOrb() {
        section(R.string.settings_section_orb, R.string.settings_section_orb_desc)
        card {
            segmentedRow(
                R.string.settings_orb_shape,
                listOf(
                    SegmentOption(getString(R.string.settings_shape_circle), { settings.orbShape == AppSettings.OrbShape.CIRCLE }) {
                        settings.orbShape = AppSettings.OrbShape.CIRCLE
                    },
                    SegmentOption(getString(R.string.settings_shape_square), { settings.orbShape == AppSettings.OrbShape.ROUNDED_SQUARE }) {
                        settings.orbShape = AppSettings.OrbShape.ROUNDED_SQUARE
                    },
                    SegmentOption(getString(R.string.settings_shape_capsule), { settings.orbShape == AppSettings.OrbShape.CAPSULE }) {
                        settings.orbShape = AppSettings.OrbShape.CAPSULE
                    },
                ),
            )
            faderRow(
                labelRes = R.string.settings_orb_size,
                min = 44f, max = 112f,
                value = settings.orbSizeDp.toFloat(),
                unit = "dp",
            ) { settings.orbSizeDp = it.toInt().coerceIn(44, 112) }
            faderRow(
                labelRes = R.string.settings_orb_margin,
                min = 0f, max = 48f,
                value = settings.orbEdgeMarginDp.toFloat(),
                unit = "dp",
            ) { settings.orbEdgeMarginDp = it.toInt().coerceIn(0, 48) }
            toggleRow(
                R.string.settings_orb_glow, R.string.settings_orb_glow_desc,
                settings.hubRingGlow,
            ) { settings.hubRingGlow = it }
            toggleRow(
                R.string.settings_hub_autostart, R.string.settings_hub_autostart_desc,
                settings.hubAutoStart,
            ) { settings.hubAutoStart = it }
            toggleRow(
                R.string.settings_hub_search, R.string.settings_hub_search_desc,
                settings.hubShowSearch,
            ) { settings.hubShowSearch = it }
            divider()
            buttonRow(R.string.settings_orb_reset_anchor, R.drawable.ic_move) {
                settings.orbAnchorX = 0.9f
                settings.orbAnchorY = 0.78f
                show(getString(R.string.settings_orb_reset_done), CommandFeedback.Kind.SUCCESS)
            }
        }
    }

    // ------------------------------------------------------------------ hud

    private fun buildHud() {
        section(R.string.settings_section_hud, R.string.settings_section_hud_desc)
        card {
            segmentedRow(
                R.string.settings_hud_mode,
                listOf(
                    SegmentOption(getString(R.string.hud_mode_mini), { settings.hudMode == AppSettings.HudMode.MINI }) {
                        settings.hudMode = AppSettings.HudMode.MINI
                    },
                    SegmentOption(getString(R.string.hud_mode_full), { settings.hudMode == AppSettings.HudMode.FULL }) {
                        settings.hudMode = AppSettings.HudMode.FULL
                    },
                    SegmentOption(getString(R.string.hud_mode_graph), { settings.hudMode == AppSettings.HudMode.GRAPH }) {
                        settings.hudMode = AppSettings.HudMode.GRAPH
                    },
                ),
            )
            faderRow(
                labelRes = R.string.settings_hud_refresh,
                min = 250f, max = 3000f,
                value = settings.hudRefreshMs.toFloat(),
                unit = "ms",
            ) { settings.hudRefreshMs = it.toLong().coerceIn(250L, 3000L) }
            faderRow(
                labelRes = R.string.settings_hud_scale,
                min = 70f, max = 160f,
                value = settings.hudScale * 100f,
                unit = "%",
            ) { settings.hudScale = (it / 100f).coerceIn(0.7f, 1.6f) }
            segmentedRow(
                R.string.settings_hud_fps_source,
                listOf(
                    SegmentOption(getString(R.string.settings_fps_auto), { settings.hudFpsSource == AppSettings.FpsSource.AUTO }) {
                        settings.hudFpsSource = AppSettings.FpsSource.AUTO
                    },
                    SegmentOption(getString(R.string.settings_fps_choreographer), { settings.hudFpsSource == AppSettings.FpsSource.CHOREOGRAPHER }) {
                        settings.hudFpsSource = AppSettings.FpsSource.CHOREOGRAPHER
                    },
                    SegmentOption(getString(R.string.settings_fps_surfaceflinger), { settings.hudFpsSource == AppSettings.FpsSource.SURFACE_FLINGER }) {
                        settings.hudFpsSource = AppSettings.FpsSource.SURFACE_FLINGER
                    },
                ),
            )
            paragraph(R.string.settings_hud_fps_source_desc)
            divider()
            metricToggle("fps", R.string.settings_metric_fps)
            metricToggle("frame", R.string.settings_metric_frame)
            metricToggle("cpu", R.string.settings_metric_cpu)
            metricToggle("ram", R.string.settings_metric_ram)
            metricToggle("temp", R.string.settings_metric_temp)
            metricToggle("batt", R.string.settings_metric_batt)
            metricToggle("net", R.string.settings_metric_net)
            metricToggle("drops", R.string.settings_metric_drops)
            toggleRow(
                R.string.settings_hud_ping, R.string.settings_hud_ping_desc,
                settings.hudPingEnabled,
            ) { settings.hudPingEnabled = it }
            toggleRow(
                R.string.settings_hud_session_autostart, R.string.settings_hud_session_autostart_desc,
                settings.hudAutoStartWithSession,
            ) { settings.hudAutoStartWithSession = it }
            divider()
            buttonRow(R.string.settings_hud_reset_anchor, R.drawable.ic_move) {
                settings.hudAnchorX = 0.5f
                settings.hudAnchorY = 0.06f
                show(getString(R.string.settings_hud_reset_done), CommandFeedback.Kind.SUCCESS)
            }
        }
    }

    private fun LinearLayout.metricToggle(metric: String, labelRes: Int) {
        toggleRow(labelRes, null, metric in settings.hudMetrics) { checked ->
            settings.hudMetrics = if (checked) {
                settings.hudMetrics + metric
            } else {
                settings.hudMetrics - metric
            }
        }
    }

    // ------------------------------------------------------------------ fx

    private fun buildFx() {
        section(R.string.settings_section_fx, R.string.settings_section_fx_desc)
        card {
            textRow(
                R.string.settings_fx_preset,
                getString(FxPresets.byId(settings.fxPreset).nameRes),
                tokens.accent,
            )
            FxPresets.ALL.chunked(3).forEach { chunk ->
                val row = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                chunk.forEach { preset ->
                    val button = com.dynablox.launcher.design.widgets.PhysicalButton(this@SettingsActivity).apply {
                        label = getString(preset.nameRes)
                        shape = com.dynablox.launcher.design.widgets.PhysicalButton.Shape.CAPSULE
                        isActivated = settings.fxPreset == preset.id
                        setOnClickListener {
                            settings.fxPreset = preset.id
                            if (container.overlays.isFxRunning) container.overlays.startFx()
                            refreshContent()
                        }
                    }
                    row.addView(
                        button,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6f).toInt()
                        },
                    )
                }
                addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6f).toInt() })
            }
            faderRow(
                labelRes = R.string.settings_fx_amount, min = 0f, max = 100f,
                value = settings.fxAmount * 100f, unit = "%",
            ) { settings.fxAmount = (it / 100f).coerceIn(0f, 1f) }
            faderRow(
                labelRes = R.string.settings_fx_grain, min = 0f, max = 100f,
                value = settings.fxGrain * 100f, unit = "%",
            ) { settings.fxGrain = (it / 100f).coerceIn(0f, 1f) }
            faderRow(
                labelRes = R.string.settings_fx_opacity, min = 0f, max = 100f,
                value = settings.fxOpacity * 100f, unit = "%",
            ) { settings.fxOpacity = (it / 100f).coerceIn(0f, 1f) }
            divider()
            buttonRow(
                R.string.settings_fx_toggle,
                R.drawable.ic_eye,
                descriptionRes = R.string.settings_fx_toggle_desc,
            ) {
                val running = container.overlays.toggleFx()
                show(
                    getString(if (running) R.string.settings_fx_on else R.string.settings_fx_off),
                    CommandFeedback.Kind.INFO,
                )
                refreshContent()
            }
        }
    }

    // ------------------------------------------------------------------ controls

    private fun buildControls() {
        section(R.string.settings_section_controls, R.string.settings_section_controls_desc)
        card {
            textRow(
                R.string.settings_controls_preset,
                getString(ControlPresets.byId(settings.controlsPresetId).nameRes),
                tokens.accent,
            )
            ControlPresets.ALL.forEach { preset ->
                actionRow(
                    label = getString(preset.nameRes),
                    subLabel = getString(preset.descriptionRes),
                    iconRes = R.drawable.ic_gamepad,
                    accent = if (settings.controlsPresetId == preset.id) tokens.accent else null,
                ) {
                    settings.controlsPresetId = preset.id
                    refreshContent()
                }
            }
            divider()
            segmentedRow(
                R.string.settings_controls_backend,
                listOf(
                    SegmentOption(getString(R.string.ctrl_method_auto), { settings.controlsBackend == AppSettings.InjectionBackend.AUTO }) {
                        settings.controlsBackend = AppSettings.InjectionBackend.AUTO
                    },
                    SegmentOption(getString(R.string.ctrl_method_shizuku), { settings.controlsBackend == AppSettings.InjectionBackend.SHIZUKU }) {
                        settings.controlsBackend = AppSettings.InjectionBackend.SHIZUKU
                    },
                    SegmentOption(getString(R.string.ctrl_method_accessibility), { settings.controlsBackend == AppSettings.InjectionBackend.ACCESSIBILITY }) {
                        settings.controlsBackend = AppSettings.InjectionBackend.ACCESSIBILITY
                    },
                ),
            )
            textRow(
                R.string.settings_controls_active_backend,
                methodLabel(container.injector.activeMethod()),
                if (container.inputReady()) tokens.success else tokens.danger,
            )
            faderRow(
                labelRes = R.string.settings_controls_opacity, min = 20f, max = 100f,
                value = settings.controlsOpacity * 100f, unit = "%",
            ) { settings.controlsOpacity = (it / 100f).coerceIn(0.2f, 1f) }
            faderRow(
                labelRes = R.string.settings_controls_scale, min = 60f, max = 180f,
                value = settings.controlsScale * 100f, unit = "%",
            ) { settings.controlsScale = (it / 100f).coerceIn(0.6f, 1.8f) }
            divider()
            buttonRow(R.string.settings_controls_open, R.drawable.ic_gamepad) {
                open(ControlsActivity::class.java)
            }
            buttonRow(
                R.string.settings_controls_toggle,
                R.drawable.ic_power,
            ) {
                val running = container.overlays.toggleControls()
                show(
                    getString(if (running) R.string.settings_controls_on else R.string.settings_controls_off),
                    CommandFeedback.Kind.INFO,
                )
                refreshContent()
            }
        }
    }

    private fun methodLabel(method: InputInjector.Method): String = getString(
        when (method) {
            InputInjector.Method.SHIZUKU -> R.string.ctrl_method_shizuku_active
            InputInjector.Method.ACCESSIBILITY -> R.string.ctrl_method_accessibility_active
            InputInjector.Method.NONE -> R.string.ctrl_method_none
        },
    )

    // ------------------------------------------------------------------ data

    private fun buildData() {
        section(R.string.settings_section_data, R.string.settings_section_data_desc)
        card {
            textRow(R.string.settings_cache, Fmt.megabytes(container.optimizer.cacheSizeKb() / 1024f, 1))
            buttonRow(R.string.settings_clear_cache, R.drawable.ic_trash) {
                launchSafely {
                    val kb = container.clearOwnCache()
                    show(
                        getString(R.string.settings_cache_cleared, Fmt.megabytes(kb / 1024f, 1)),
                        CommandFeedback.Kind.SUCCESS,
                    )
                    refreshContent()
                }
            }
            buttonRow(R.string.settings_reset, R.drawable.ic_alert, accent = tokens.danger) {
                settings.resetAll()
                show(getString(R.string.settings_reset_done), CommandFeedback.Kind.SUCCESS)
                recreate()
            }
        }
    }
}

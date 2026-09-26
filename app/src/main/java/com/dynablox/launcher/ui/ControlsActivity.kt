package com.dynablox.launcher.ui

import android.widget.LinearLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.controls.ClusterSide
import com.dynablox.launcher.controls.ControlPresets
import com.dynablox.launcher.controls.InputInjector
import com.dynablox.launcher.controls.PadButton
import com.dynablox.launcher.controls.PadGesture
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.util.Fmt

/**
 * Virtual controls desk.
 *
 * Shows exactly what each key does — the gesture and the normalised screen coordinate it is injected
 * at — and lets the user fire any of them once to verify the backend before a session. No key here
 * is decorative: every press goes through [InputInjector].
 */
class ControlsActivity : DbxActivity() {

    override val titleRes: Int = R.string.title_controls
    override val subtitleRes: Int = R.string.controls_subtitle
    override val headerIconRes: Int = R.drawable.ic_gamepad

    private var lastLatency: Float = 0f

    override fun buildContent(root: LinearLayout) {
        buildStatus()
        buildPresets()
        buildKeys()
        buildAppearance()
    }

    private fun buildStatus() {
        section(R.string.controls_section_status, R.string.controls_section_status_desc)
        card {
            ledRow(
                getString(R.string.controls_led_shizuku),
                container.shizuku.isGranted,
                if (container.shizuku.isGranted) tokens.accent else tokens.ledOff,
            )
            ledRow(
                getString(R.string.controls_led_accessibility),
                com.dynablox.launcher.accessibility.DynabloxAccessibilityService.isEnabled,
                if (com.dynablox.launcher.accessibility.DynabloxAccessibilityService.isEnabled) {
                    tokens.accent
                } else {
                    tokens.ledOff
                },
            )
            textRow(
                R.string.controls_active_backend,
                getString(
                    when (container.injector.activeMethod()) {
                        InputInjector.Method.SHIZUKU -> R.string.ctrl_method_shizuku_active
                        InputInjector.Method.ACCESSIBILITY -> R.string.ctrl_method_accessibility_active
                        InputInjector.Method.NONE -> R.string.ctrl_method_none
                    },
                ),
                if (container.inputReady()) tokens.success else tokens.danger,
            )
            textRow(
                R.string.controls_latency,
                if (lastLatency > 0f) Fmt.ms(lastLatency) else getString(R.string.state_unknown),
            )
            if (!container.inputReady()) {
                paragraph(R.string.controls_needs_backend_desc)
                buttonRow(R.string.controls_open_permissions, R.drawable.ic_shield) {
                    open(PermissionsActivity::class.java)
                }
            }
            buttonRow(
                R.string.controls_test_center,
                R.drawable.ic_target,
                descriptionRes = R.string.controls_test_center_desc,
                enabled = container.inputReady(),
            ) { testCenterTap() }
            divider()
            buttonRow(
                R.string.controls_toggle_overlay,
                R.drawable.ic_power,
                descriptionRes = R.string.controls_toggle_overlay_desc,
            ) {
                val running = container.overlays.toggleControls()
                show(
                    getString(if (running) R.string.controls_started else R.string.controls_stopped),
                    CommandFeedback.Kind.INFO,
                )
                refreshContent()
            }
        }
    }

    private fun testCenterTap() {
        launchSafely {
            val metrics = container.windows.metrics()
            val result = container.injector.tap(metrics.widthPx / 2f, metrics.heightPx / 2f)
            lastLatency = result.latencyMs.toFloat()
            show(
                getString(
                    R.string.ctrl_test_result,
                    getString(
                        when (result.method) {
                            InputInjector.Method.SHIZUKU -> R.string.ctrl_method_shizuku
                            InputInjector.Method.ACCESSIBILITY -> R.string.ctrl_method_accessibility
                            InputInjector.Method.NONE -> R.string.ctrl_method_none
                        },
                    ),
                    Fmt.number(result.latencyMs.toFloat(), 0),
                ),
                if (result.success) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.ERROR,
            )
            refreshContent()
        }
    }

    private fun buildPresets() {
        section(R.string.controls_section_presets, R.string.controls_section_presets_desc)
        card {
            ControlPresets.ALL.forEach { preset ->
                val selected = settings.controlsPresetId == preset.id
                actionRow(
                    label = getString(preset.nameRes),
                    subLabel = getString(preset.descriptionRes),
                    iconRes = R.drawable.ic_grid,
                    accent = if (selected) tokens.accent else null,
                ) {
                    settings.controlsPresetId = preset.id
                    show(getString(preset.nameRes), CommandFeedback.Kind.SUCCESS)
                    refreshContent()
                }
            }
        }
    }

    private fun buildKeys() {
        val preset = ControlPresets.byId(settings.controlsPresetId)
        section(
            R.string.controls_section_keys,
            R.string.controls_section_keys_desc,
        )
        preset.clusters.forEach { cluster ->
            card {
                textRow(
                    if (cluster.side == ClusterSide.LEFT) {
                        R.string.controls_cluster_left
                    } else {
                        R.string.controls_cluster_right
                    },
                    getString(R.string.controls_keys_count, cluster.buttons.size),
                    tokens.textSecondary,
                )
                cluster.buttons.forEach { button -> keyRow(button) }
            }
        }
    }

    private fun LinearLayout.keyRow(button: PadButton) {
        actionRow(
            label = getString(button.labelRes),
            subLabel = getString(
                R.string.controls_key_meta,
                gestureLabel(button.gesture),
                Fmt.number(button.targetX * 100f, 0),
                Fmt.number(button.targetY * 100f, 0),
            ),
            iconRes = R.drawable.ic_grip,
            enabled = container.inputReady(),
        ) { inject(button) }
    }

    private fun inject(button: PadButton) {
        launchSafely {
            val metrics = container.windows.metrics()
            val result = container.injector.gesture(
                button.gesture, button.targetX, button.targetY,
                metrics.widthPx, metrics.heightPx,
            )
            lastLatency = result.latencyMs.toFloat()
            show(
                if (result.success) {
                    getString(R.string.controls_injected, gestureLabel(button.gesture), Fmt.ms(result.latencyMs.toFloat()))
                } else {
                    result.message ?: getString(R.string.ctrl_injection_failed)
                },
                if (result.success) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.ERROR,
            )
            refreshContent()
        }
    }

    private fun gestureLabel(gesture: PadGesture): String = getString(
        when (gesture) {
            PadGesture.TAP -> R.string.ctrl_gesture_tap
            PadGesture.HOLD_SHORT -> R.string.ctrl_gesture_hold_short
            PadGesture.HOLD_LONG -> R.string.ctrl_gesture_hold_long
            PadGesture.SWIPE_UP -> R.string.ctrl_gesture_swipe_up
            PadGesture.SWIPE_DOWN -> R.string.ctrl_gesture_swipe_down
            PadGesture.SWIPE_LEFT -> R.string.ctrl_gesture_swipe_left
            PadGesture.SWIPE_RIGHT -> R.string.ctrl_gesture_swipe_right
            PadGesture.KEY_BACK -> R.string.ctrl_gesture_back
            PadGesture.KEY_HOME -> R.string.ctrl_gesture_home
            PadGesture.KEY_RECENTS -> R.string.ctrl_gesture_recents
        },
    )

    private fun buildAppearance() {
        section(R.string.controls_section_appearance)
        card {
            faderRow(
                labelRes = R.string.settings_controls_opacity, min = 20f, max = 100f,
                value = settings.controlsOpacity * 100f, unit = "%",
            ) { settings.controlsOpacity = (it / 100f).coerceIn(0.2f, 1f) }
            faderRow(
                labelRes = R.string.settings_controls_scale, min = 60f, max = 180f,
                value = settings.controlsScale * 100f, unit = "%",
            ) { settings.controlsScale = (it / 100f).coerceIn(0.6f, 1.8f) }
            divider()
            buttonRow(R.string.controls_reset_anchors, R.drawable.ic_move) {
                settings.controlsLeftAnchorX = 0.16f
                settings.controlsLeftAnchorY = 0.80f
                settings.controlsRightAnchorX = 0.84f
                settings.controlsRightAnchorY = 0.80f
                show(getString(R.string.controls_anchors_reset), CommandFeedback.Kind.SUCCESS)
            }
            paragraph(R.string.controls_drag_hint)
        }
    }
}

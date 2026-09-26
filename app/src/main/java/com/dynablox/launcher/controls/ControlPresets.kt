package com.dynablox.launcher.controls

import androidx.annotation.StringRes
import com.dynablox.launcher.R

/** What a virtual button does to the real game screen. */
enum class PadGesture {
    TAP,
    HOLD_SHORT,
    HOLD_LONG,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    KEY_BACK,
    KEY_HOME,
    KEY_RECENTS,
}

/**
 * A virtual button.
 *
 * `localX`/`localY` position the key inside its cluster (0..1), `targetX`/`targetY` are the
 * *normalised screen coordinates* the gesture is injected at — i.e. where the game's own control
 * lives. That is what makes the pad a remapper instead of a second, unreachable input layer.
 */
data class PadButton(
    val id: String,
    @StringRes val labelRes: Int,
    val localX: Float,
    val localY: Float,
    val sizeDp: Float,
    val gesture: PadGesture,
    val targetX: Float,
    val targetY: Float,
)

enum class ClusterSide { LEFT, RIGHT }

data class PadCluster(
    val side: ClusterSide,
    val buttons: List<PadButton>,
)

data class ControlPreset(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val clusters: List<PadCluster>,
)

/**
 * Built-in usage presets. Target coordinates follow the default Roblox mobile touch layout
 * (movement thumb zone bottom-left, action buttons bottom-right); users can nudge them from the
 * controls screen when a game moves its buttons.
 */
object ControlPresets {

    private val leftDpad = PadCluster(
        side = ClusterSide.LEFT,
        buttons = listOf(
            PadButton("up", R.string.ctrl_dpad, 0.5f, 0.08f, 46f, PadGesture.SWIPE_UP, 0.18f, 0.62f),
            PadButton("left", R.string.ctrl_button_left, 0.08f, 0.5f, 46f, PadGesture.SWIPE_LEFT, 0.18f, 0.62f),
            PadButton("right", R.string.ctrl_button_right, 0.92f, 0.5f, 46f, PadGesture.SWIPE_RIGHT, 0.18f, 0.62f),
            PadButton("down", R.string.ctrl_dpad, 0.5f, 0.92f, 46f, PadGesture.SWIPE_DOWN, 0.18f, 0.62f),
        ),
    )

    val ALL: List<ControlPreset> = listOf(
        ControlPreset(
            id = "fling",
            nameRes = R.string.ctrl_preset_fling,
            descriptionRes = R.string.ctrl_preset_fling_desc,
            clusters = listOf(
                leftDpad,
                PadCluster(
                    side = ClusterSide.RIGHT,
                    buttons = listOf(
                        PadButton("jump", R.string.ctrl_button_jump, 0.78f, 0.22f, 58f, PadGesture.TAP, 0.88f, 0.66f),
                        PadButton("a", R.string.ctrl_button_a, 0.24f, 0.5f, 52f, PadGesture.TAP, 0.76f, 0.78f),
                        PadButton("b", R.string.ctrl_button_b, 0.78f, 0.78f, 52f, PadGesture.HOLD_SHORT, 0.64f, 0.86f),
                    ),
                ),
            ),
        ),
        ControlPreset(
            id = "combat",
            nameRes = R.string.ctrl_preset_combat,
            descriptionRes = R.string.ctrl_preset_combat_desc,
            clusters = listOf(
                PadCluster(
                    side = ClusterSide.LEFT,
                    buttons = listOf(
                        PadButton("look", R.string.ctrl_stick, 0.5f, 0.5f, 74f, PadGesture.SWIPE_UP, 0.30f, 0.45f),
                        PadButton("crouch", R.string.ctrl_button_crouch, 0.12f, 0.92f, 44f, PadGesture.TAP, 0.60f, 0.92f),
                    ),
                ),
                PadCluster(
                    side = ClusterSide.RIGHT,
                    buttons = listOf(
                        PadButton("fire", R.string.ctrl_button_fire, 0.72f, 0.30f, 62f, PadGesture.HOLD_SHORT, 0.90f, 0.60f),
                        PadButton("jump", R.string.ctrl_button_jump, 0.24f, 0.52f, 54f, PadGesture.TAP, 0.86f, 0.78f),
                        PadButton("reload", R.string.ctrl_button_reload, 0.76f, 0.82f, 46f, PadGesture.TAP, 0.70f, 0.90f),
                    ),
                ),
            ),
        ),
        ControlPreset(
            id = "roleplay",
            nameRes = R.string.ctrl_preset_rp,
            descriptionRes = R.string.ctrl_preset_rp_desc,
            clusters = listOf(
                PadCluster(
                    side = ClusterSide.LEFT,
                    buttons = listOf(
                        PadButton("chat", R.string.ctrl_button_chat, 0.30f, 0.5f, 54f, PadGesture.TAP, 0.08f, 0.10f),
                        PadButton("emote", R.string.ctrl_button_emote, 0.86f, 0.5f, 54f, PadGesture.TAP, 0.92f, 0.20f),
                    ),
                ),
                PadCluster(
                    side = ClusterSide.RIGHT,
                    buttons = listOf(
                        PadButton("sit", R.string.ctrl_button_sit, 0.30f, 0.5f, 54f, PadGesture.HOLD_SHORT, 0.50f, 0.80f),
                        PadButton("camera", R.string.ctrl_button_camera, 0.86f, 0.5f, 54f, PadGesture.TAP, 0.94f, 0.08f),
                    ),
                ),
            ),
        ),
        ControlPreset(
            id = "driving",
            nameRes = R.string.ctrl_preset_drive,
            descriptionRes = R.string.ctrl_preset_drive_desc,
            clusters = listOf(
                PadCluster(
                    side = ClusterSide.LEFT,
                    buttons = listOf(
                        PadButton("left", R.string.ctrl_button_left, 0.26f, 0.5f, 60f, PadGesture.HOLD_LONG, 0.12f, 0.72f),
                        PadButton("right", R.string.ctrl_button_right, 0.84f, 0.5f, 60f, PadGesture.HOLD_LONG, 0.26f, 0.72f),
                    ),
                ),
                PadCluster(
                    side = ClusterSide.RIGHT,
                    buttons = listOf(
                        PadButton("brake", R.string.ctrl_button_brake, 0.28f, 0.5f, 58f, PadGesture.HOLD_LONG, 0.90f, 0.80f),
                        PadButton("horn", R.string.ctrl_button_horn, 0.84f, 0.5f, 48f, PadGesture.TAP, 0.80f, 0.60f),
                    ),
                ),
            ),
        ),
        ControlPreset(
            id = "minimal",
            nameRes = R.string.ctrl_preset_custom,
            descriptionRes = R.string.ctrl_preset_custom_desc,
            clusters = listOf(
                PadCluster(
                    side = ClusterSide.RIGHT,
                    buttons = listOf(
                        PadButton("jump", R.string.ctrl_button_jump, 0.5f, 0.24f, 58f, PadGesture.TAP, 0.88f, 0.66f),
                        PadButton("camera", R.string.ctrl_button_camera, 0.5f, 0.82f, 48f, PadGesture.TAP, 0.94f, 0.08f),
                    ),
                ),
            ),
        ),
    )

    fun byId(id: String): ControlPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

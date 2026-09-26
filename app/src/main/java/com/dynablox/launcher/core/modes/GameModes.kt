package com.dynablox.launcher.core.modes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dynablox.launcher.R

/**
 * Performance modes.
 *
 * A mode is not a label on a button: it is a named bundle of the *real* optimizer actions this app
 * already performs, plus the HUD/telemetry posture that goes with it. Picking one applies the
 * bundle and reverts whatever the previous mode had changed, so modes are mutually exclusive and
 * always reversible.
 *
 * Deliberately absent: "RAM boosters", fake CPU overclocks, GPU switches. Android exposes none of
 * those to a normal app, and a mode that claims them would be theatre. What a mode can honestly do
 * is stop background work, drop animation cost, hold a performance hint, guard thermals and keep
 * notifications out of the way — which is exactly what the bundles below contain.
 */
data class GameMode(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val taglineRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    /** Optimizer entry ids applied when this mode is engaged. */
    val actions: Set<String>,
    /** Accent used by the rail, the chevron meters and the mode banner. */
    val tint: ModeTint,
    /** Aggressiveness, 0..3 — drives the chevron count lit on the side meters. */
    val intensity: Int,
    /** Whether the mode watches thermals and backs off when the device throttles. */
    val thermalGuard: Boolean,
) {
    /** Modes that touch privileged state need Shizuku; the engine degrades instead of lying. */
    val needsShizuku: Boolean get() = actions.any { it in PRIVILEGED_ACTIONS }

    companion object {
        /** Optimizer ids that only work through the shell identity. */
        val PRIVILEGED_ACTIONS = setOf("anim_scales", "force_stop", "kill_bg")
    }
}

/** Restrained accent set — one per mode, resolved against the active theme. */
enum class ModeTint { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

object GameModes {

    /**
     * Idle. Nothing is held, nothing is suppressed — the baseline the other modes revert to.
     * It exists as a real entry so "no mode" is a deliberate state instead of an empty variable.
     */
    val OFF = GameMode(
        id = "off",
        nameRes = R.string.mode_off,
        taglineRes = R.string.mode_off_tagline,
        descriptionRes = R.string.mode_off_desc,
        iconRes = R.drawable.ic_power,
        actions = emptySet(),
        tint = ModeTint.NEUTRAL,
        intensity = 0,
        thermalGuard = false,
    )

    /** Long sessions: trims cache and guards heat, touches nothing the user would notice. */
    val ECO = GameMode(
        id = "eco",
        nameRes = R.string.mode_eco,
        taglineRes = R.string.mode_eco_tagline,
        descriptionRes = R.string.mode_eco_desc,
        iconRes = R.drawable.ic_leaf,
        actions = setOf("cache", "thermal"),
        tint = ModeTint.SUCCESS,
        intensity = 1,
        thermalGuard = true,
    )

    /** The default: everything safe, nothing privileged. Works with zero permissions granted. */
    val BALANCED = GameMode(
        id = "balanced",
        nameRes = R.string.mode_balanced,
        taglineRes = R.string.mode_balanced_tagline,
        descriptionRes = R.string.mode_balanced_desc,
        iconRes = R.drawable.ic_gauge,
        actions = setOf("battery", "cache", "thermal", "perf_hint"),
        tint = ModeTint.ACCENT,
        intensity = 2,
        thermalGuard = true,
    )

    /** Competitive: adds the privileged actions and silences interruptions. */
    val TURBO = GameMode(
        id = "turbo",
        nameRes = R.string.mode_turbo,
        taglineRes = R.string.mode_turbo_tagline,
        descriptionRes = R.string.mode_turbo_desc,
        iconRes = R.drawable.ic_bolt,
        actions = setOf("battery", "cache", "thermal", "perf_hint", "kill_bg", "anim_scales", "dnd"),
        tint = ModeTint.WARNING,
        intensity = 3,
        thermalGuard = true,
    )

    /**
     * Everything TURBO does plus force-stopping the apps the user picked.
     *
     * The thermal guard stays ON: a mode that ignores heat throttles the device into being slower
     * than the mode it replaced, which would make the whole feature a lie.
     */
    val BATTLE = GameMode(
        id = "battle",
        nameRes = R.string.mode_battle,
        taglineRes = R.string.mode_battle_tagline,
        descriptionRes = R.string.mode_battle_desc,
        iconRes = R.drawable.ic_crosshair,
        actions = setOf(
            "battery", "cache", "thermal", "perf_hint",
            "kill_bg", "anim_scales", "dnd", "force_stop",
        ),
        tint = ModeTint.DANGER,
        intensity = 3,
        thermalGuard = true,
    )

    /** Ordered as they appear on the rail, calmest to most aggressive. */
    val ALL: List<GameMode> = listOf(OFF, ECO, BALANCED, TURBO, BATTLE)

    /** Selectable modes — OFF is reachable by disengaging, not by picking it on the rail. */
    val SELECTABLE: List<GameMode> = ALL.filter { it.id != OFF.id }

    val DEFAULT: GameMode = BALANCED

    /** Unknown ids fall back to [OFF] so a renamed preference can never engage something. */
    fun byId(id: String?): GameMode = ALL.firstOrNull { it.id == id } ?: OFF
}

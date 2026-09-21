package com.dynablox.launcher.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for every user-tweakable knob in the product.
 *
 * Backed by SharedPreferences (cheap, synchronous, no extra dependency) and mirrored into a
 * [changes] StateFlow so views and services can react without polling. Every write bumps the
 * revision counter, which is all a redraw needs.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private fun commit(editor: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().also(editor).apply()
        _changes.value = _changes.value + 1
    }

    // ---------------------------------------------------------------- enums

    enum class ThemeMode { DARK, LIGHT, SYSTEM }

    enum class OrbShape { CIRCLE, ROUNDED_SQUARE, CAPSULE }

    enum class HudMode { MINI, FULL, GRAPH }

    enum class FpsSource { AUTO, CHOREOGRAPHER, SURFACE_FLINGER }

    enum class AnimationStyle { PRECISE, SPRING, INSTANT }

    enum class InjectionBackend { AUTO, SHIZUKU, ACCESSIBILITY }

    // ---------------------------------------------------------------- appearance

    var themeMode: ThemeMode
        get() = enum(KEY_THEME, ThemeMode.DARK)
        set(value) = commit { putString(KEY_THEME, value.name) }

    /** 0.85 .. 1.30 — multiplies every dp in the design system. */
    var densityScale: Float
        get() = prefs.getFloat(KEY_DENSITY, 1f).coerceIn(0.85f, 1.30f)
        set(value) = commit { putFloat(KEY_DENSITY, value.coerceIn(0.85f, 1.30f)) }

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = commit { putBoolean(KEY_REDUCE_MOTION, value) }

    var translucentSurfaces: Boolean
        get() = prefs.getBoolean(KEY_TRANSLUCENT, true)
        set(value) = commit { putBoolean(KEY_TRANSLUCENT, value) }

    /** 0 = flat, 1 = deep studio shadows. */
    var shadowIntensity: Float
        get() = prefs.getFloat(KEY_SHADOW, 0.75f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_SHADOW, value.coerceIn(0f, 1f)) }

    var animationStyle: AnimationStyle
        get() = enum(KEY_ANIM_STYLE, AnimationStyle.SPRING)
        set(value) = commit { putString(KEY_ANIM_STYLE, value.name) }

    // ---------------------------------------------------------------- MenuHub orb

    var orbShape: OrbShape
        get() = enum(KEY_ORB_SHAPE, OrbShape.CIRCLE)
        set(value) = commit { putString(KEY_ORB_SHAPE, value.name) }

    var orbSizeDp: Int
        get() = prefs.getInt(KEY_ORB_SIZE, 64).coerceIn(48, 104)
        set(value) = commit { putInt(KEY_ORB_SIZE, value.coerceIn(48, 104)) }

    /** Normalised screen coordinates (0..1) of the orb centre. */
    var orbAnchorX: Float
        get() = prefs.getFloat(KEY_ORB_X, 0.88f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_ORB_X, value.coerceIn(0f, 1f)) }

    var orbAnchorY: Float
        get() = prefs.getFloat(KEY_ORB_Y, 0.72f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_ORB_Y, value.coerceIn(0f, 1f)) }

    var orbEdgeMarginDp: Int
        get() = prefs.getInt(KEY_ORB_MARGIN, 12).coerceIn(0, 48)
        set(value) = commit { putInt(KEY_ORB_MARGIN, value.coerceIn(0, 48)) }

    var hubAutoStart: Boolean
        get() = prefs.getBoolean(KEY_HUB_AUTOSTART, false)
        set(value) = commit { putBoolean(KEY_HUB_AUTOSTART, value) }

    var hubShowSearch: Boolean
        get() = prefs.getBoolean(KEY_HUB_SEARCH, true)
        set(value) = commit { putBoolean(KEY_HUB_SEARCH, value) }

    var hubRingGlow: Boolean
        get() = prefs.getBoolean(KEY_HUB_GLOW, true)
        set(value) = commit { putBoolean(KEY_HUB_GLOW, value) }

    /** Starts the performance HUD automatically whenever a game session is launched. */
    var hudAutoStartWithSession: Boolean
        get() = prefs.getBoolean(KEY_HUD_AUTOSTART_SESSION, true)
        set(value) = commit { putBoolean(KEY_HUD_AUTOSTART_SESSION, value) }

    /** Command ids in the user's preferred order. */
    var hubOrder: List<String>
        get() = prefs.getString(KEY_HUB_ORDER, null)?.split('|')?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = commit { putString(KEY_HUB_ORDER, value.joinToString("|")) }

    var hubFavorites: Set<String>
        get() = prefs.getStringSet(KEY_HUB_FAVS, emptySet())?.toSet() ?: emptySet()
        set(value) = commit { putStringSet(KEY_HUB_FAVS, value.toSet()) }

    var hubHiddenCategories: Set<String>
        get() = prefs.getStringSet(KEY_HUB_HIDDEN, emptySet())?.toSet() ?: emptySet()
        set(value) = commit { putStringSet(KEY_HUB_HIDDEN, value.toSet()) }

    fun toggleFavorite(id: String) {
        val current = hubFavorites.toMutableSet()
        if (!current.remove(id)) current.add(id)
        hubFavorites = current
    }

    fun toggleCategoryHidden(id: String) {
        val current = hubHiddenCategories.toMutableSet()
        if (!current.remove(id)) current.add(id)
        hubHiddenCategories = current
    }

    // ---------------------------------------------------------------- performance HUD

    var hudMode: HudMode
        get() = enum(KEY_HUD_MODE, HudMode.FULL)
        set(value) = commit { putString(KEY_HUD_MODE, value.name) }

    var hudRefreshMs: Long
        get() = prefs.getLong(KEY_HUD_REFRESH, 1000L).coerceIn(250L, 5000L)
        set(value) = commit { putLong(KEY_HUD_REFRESH, value.coerceIn(250L, 5000L)) }

    var hudMetrics: Set<String>
        get() = prefs.getStringSet(KEY_HUD_METRICS, DEFAULT_METRICS)?.toSet() ?: DEFAULT_METRICS
        set(value) = commit { putStringSet(KEY_HUD_METRICS, value.toSet()) }

    var hudFpsSource: FpsSource
        get() = enum(KEY_HUD_FPS_SOURCE, FpsSource.AUTO)
        set(value) = commit { putString(KEY_HUD_FPS_SOURCE, value.name) }

    var hudPingEnabled: Boolean
        get() = prefs.getBoolean(KEY_HUD_PING, false)
        set(value) = commit { putBoolean(KEY_HUD_PING, value) }

    var hudPingHost: String
        get() = prefs.getString(KEY_HUD_PING_HOST, "roblox.com")?.takeIf { it.isNotBlank() }
            ?: "roblox.com"
        set(value) = commit { putString(KEY_HUD_PING_HOST, value.trim()) }

    var hudAnchorX: Float
        get() = prefs.getFloat(KEY_HUD_X, 0.06f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_HUD_X, value.coerceIn(0f, 1f)) }

    var hudAnchorY: Float
        get() = prefs.getFloat(KEY_HUD_Y, 0.14f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_HUD_Y, value.coerceIn(0f, 1f)) }

    var hudScale: Float
        get() = prefs.getFloat(KEY_HUD_SCALE, 1f).coerceIn(0.7f, 1.6f)
        set(value) = commit { putFloat(KEY_HUD_SCALE, value.coerceIn(0.7f, 1.6f)) }

    // ---------------------------------------------------------------- screen FX

    var fxPreset: String
        get() = prefs.getString(KEY_FX_PRESET, "vignette") ?: "vignette"
        set(value) = commit { putString(KEY_FX_PRESET, value) }

    var fxAmount: Float
        get() = prefs.getFloat(KEY_FX_AMOUNT, 0.55f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_FX_AMOUNT, value.coerceIn(0f, 1f)) }

    var fxGrain: Float
        get() = prefs.getFloat(KEY_FX_GRAIN, 0.12f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_FX_GRAIN, value.coerceIn(0f, 1f)) }

    var fxScanlines: Float
        get() = prefs.getFloat(KEY_FX_SCAN, 0f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_FX_SCAN, value.coerceIn(0f, 1f)) }

    var fxOpacity: Float
        get() = prefs.getFloat(KEY_FX_OPACITY, 0.85f).coerceIn(0.2f, 1f)
        set(value) = commit { putFloat(KEY_FX_OPACITY, value.coerceIn(0.2f, 1f)) }

    // ---------------------------------------------------------------- shader lab

    var shaderPreset: String
        get() = prefs.getString(KEY_SHADER_PRESET, "crt") ?: "crt"
        set(value) = commit { putString(KEY_SHADER_PRESET, value) }

    /** Stored value for a shader uniform, or [default] while the user has not touched it. */
    fun shaderParam(id: String, default: Float): Float =
        prefs.getFloat(KEY_SHADER_PARAM + id, default).coerceIn(0f, 1f)

    fun setShaderParam(id: String, value: Float) =
        commit { putFloat(KEY_SHADER_PARAM + id, value.coerceIn(0f, 1f)) }

    /** Drops a stored uniform so the preset's own default applies again. */
    fun clearShaderParam(id: String) = commit { remove(KEY_SHADER_PARAM + id) }

    // ---------------------------------------------------------------- virtual controls

    var controlsPresetId: String
        get() = prefs.getString(KEY_CTRL_PRESET, "fling") ?: "fling"
        set(value) = commit { putString(KEY_CTRL_PRESET, value) }

    var controlsBackend: InjectionBackend
        get() = enum(KEY_CTRL_BACKEND, InjectionBackend.AUTO)
        set(value) = commit { putString(KEY_CTRL_BACKEND, value.name) }

    var controlsOpacity: Float
        get() = prefs.getFloat(KEY_CTRL_OPACITY, 0.9f).coerceIn(0.25f, 1f)
        set(value) = commit { putFloat(KEY_CTRL_OPACITY, value.coerceIn(0.25f, 1f)) }

    var controlsScale: Float
        get() = prefs.getFloat(KEY_CTRL_SCALE, 1f).coerceIn(0.7f, 1.5f)
        set(value) = commit { putFloat(KEY_CTRL_SCALE, value.coerceIn(0.7f, 1.5f)) }

    var controlsLeftAnchorX: Float
        get() = prefs.getFloat(KEY_CTRL_LX, 0.16f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_CTRL_LX, value.coerceIn(0f, 1f)) }

    var controlsLeftAnchorY: Float
        get() = prefs.getFloat(KEY_CTRL_LY, 0.78f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_CTRL_LY, value.coerceIn(0f, 1f)) }

    var controlsRightAnchorX: Float
        get() = prefs.getFloat(KEY_CTRL_RX, 0.84f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_CTRL_RX, value.coerceIn(0f, 1f)) }

    var controlsRightAnchorY: Float
        get() = prefs.getFloat(KEY_CTRL_RY, 0.78f).coerceIn(0f, 1f)
        set(value) = commit { putFloat(KEY_CTRL_RY, value.coerceIn(0f, 1f)) }

    // ---------------------------------------------------------------- roblox library

    var selectedPlaceId: String
        get() = prefs.getString(KEY_PLACE_ID, "4924922222") ?: "4924922222"
        set(value) = commit { putString(KEY_PLACE_ID, value.trim()) }

    var customPlaceId: String
        get() = prefs.getString(KEY_CUSTOM_PLACE, "") ?: ""
        set(value) = commit { putString(KEY_CUSTOM_PLACE, value.trim()) }

    var lastSessionAt: Long
        get() = prefs.getLong(KEY_LAST_SESSION, 0L)
        set(value) = commit { putLong(KEY_LAST_SESSION, value) }

    var sessionsLaunched: Int
        get() = prefs.getInt(KEY_SESSIONS, 0)
        set(value) = commit { putInt(KEY_SESSIONS, value) }

    // ---------------------------------------------------------------- optimizer state

    var storedAnimationScales: String
        get() = prefs.getString(KEY_ANIM_SCALES, "") ?: ""
        set(value) = commit { putString(KEY_ANIM_SCALES, value) }

    var animationTweaked: Boolean
        get() = prefs.getBoolean(KEY_ANIM_TWEAKED, false)
        set(value) = commit { putBoolean(KEY_ANIM_TWEAKED, value) }

    var lastOptimizeAt: Long
        get() = prefs.getLong(KEY_LAST_OPTIMIZE, 0L)
        set(value) = commit { putLong(KEY_LAST_OPTIMIZE, value) }

    var enabledOptimizations: Set<String>
        get() = prefs.getStringSet(KEY_OPT_ENABLED, DEFAULT_OPTIMIZATIONS)?.toSet()
            ?: DEFAULT_OPTIMIZATIONS
        set(value) = commit { putStringSet(KEY_OPT_ENABLED, value.toSet()) }

    var forceStopSelection: Set<String>
        get() = prefs.getStringSet(KEY_OPT_FORCESTOP, emptySet())?.toSet() ?: emptySet()
        set(value) = commit { putStringSet(KEY_OPT_FORCESTOP, value.toSet()) }

    // ---------------------------------------------------------------- helpers

    private inline fun <reified T : Enum<T>> enum(key: String, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return try {
            enumValueOf<T>(raw)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    fun resetAll() {
        commit { clear() }
    }

    companion object {
        private const val FILE = "dynablox_settings"
        private const val KEY_HUD_AUTOSTART_SESSION = "hud_autostart_session"

        private const val KEY_THEME = "theme_mode"
        private const val KEY_DENSITY = "density_scale"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_TRANSLUCENT = "translucent"
        private const val KEY_SHADOW = "shadow_intensity"
        private const val KEY_ANIM_STYLE = "animation_style"

        private const val KEY_ORB_SHAPE = "orb_shape"
        private const val KEY_ORB_SIZE = "orb_size"
        private const val KEY_ORB_X = "orb_x"
        private const val KEY_ORB_Y = "orb_y"
        private const val KEY_ORB_MARGIN = "orb_margin"
        private const val KEY_HUB_AUTOSTART = "hub_autostart"
        private const val KEY_HUB_SEARCH = "hub_search"
        private const val KEY_HUB_GLOW = "hub_glow"
        private const val KEY_HUB_ORDER = "hub_order"
        private const val KEY_HUB_FAVS = "hub_favorites"
        private const val KEY_HUB_HIDDEN = "hub_hidden_categories"

        private const val KEY_HUD_MODE = "hud_mode"
        private const val KEY_HUD_REFRESH = "hud_refresh"
        private const val KEY_HUD_METRICS = "hud_metrics"
        private const val KEY_HUD_FPS_SOURCE = "hud_fps_source"
        private const val KEY_HUD_PING = "hud_ping"
        private const val KEY_HUD_PING_HOST = "hud_ping_host"
        private const val KEY_HUD_X = "hud_x"
        private const val KEY_HUD_Y = "hud_y"
        private const val KEY_HUD_SCALE = "hud_scale"

        private const val KEY_FX_PRESET = "fx_preset"
        private const val KEY_FX_AMOUNT = "fx_amount"
        private const val KEY_FX_GRAIN = "fx_grain"
        private const val KEY_FX_SCAN = "fx_scanlines"
        private const val KEY_FX_OPACITY = "fx_opacity"

        private const val KEY_SHADER_PRESET = "shader_preset"
        private const val KEY_SHADER_PARAM = "shader_param_"

        private const val KEY_CTRL_PRESET = "ctrl_preset"
        private const val KEY_CTRL_BACKEND = "ctrl_backend"
        private const val KEY_CTRL_OPACITY = "ctrl_opacity"
        private const val KEY_CTRL_SCALE = "ctrl_scale"
        private const val KEY_CTRL_LX = "ctrl_lx"
        private const val KEY_CTRL_LY = "ctrl_ly"
        private const val KEY_CTRL_RX = "ctrl_rx"
        private const val KEY_CTRL_RY = "ctrl_ry"

        private const val KEY_PLACE_ID = "selected_place"
        private const val KEY_CUSTOM_PLACE = "custom_place"
        private const val KEY_LAST_SESSION = "last_session"
        private const val KEY_SESSIONS = "sessions_launched"

        private const val KEY_ANIM_SCALES = "stored_anim_scales"
        private const val KEY_ANIM_TWEAKED = "anim_tweaked"
        private const val KEY_LAST_OPTIMIZE = "last_optimize"
        private const val KEY_OPT_ENABLED = "opt_enabled"
        private const val KEY_OPT_FORCESTOP = "opt_forcestop"

        val DEFAULT_METRICS: Set<String> = setOf(
            "fps", "frame", "cpu", "ram", "temp", "batt", "net", "drops",
        )

        val DEFAULT_OPTIMIZATIONS: Set<String> = setOf(
            "battery", "cache", "thermal", "kill_bg",
        )
    }
}

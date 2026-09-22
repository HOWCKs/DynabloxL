package com.dynablox.launcher.design

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Runtime theme authority. Activities call [applyTo] before `super.onCreate()`, overlay services
 * call [wrap] so their views inherit exactly the same material palette.
 */
object SkeuoTheme {

    private val _revision = MutableStateFlow(0)

    /** User preference: 0 = flat, 1 = deep studio shadows. Read by every material painter. */
    @Volatile
    var shadowIntensity: Float = 0.75f
        private set

    /** User preference: smoked glass + backdrop blur vs. opaque surfaces. */
    @Volatile
    var translucent: Boolean = true
        private set

    /**
     * Active material vocabulary. Read by [Materials.paint] on every frame, so it is the single
     * switch that migrates the entire product between finishes.
     */
    @Volatile
    var finish: Finish = Finish.CLAY
        private set

    /** Bumped whenever the theme, scale or motion settings change. */
    val revision: StateFlow<Int> = _revision

    /** Pushes settings into the global motion/dimension helpers. Call on every settings change. */
    fun sync(settings: AppSettings) {
        Motion.apply(settings)
        Dimens.apply(settings)
        shadowIntensity = settings.shadowIntensity
        translucent = settings.translucentSurfaces
        finish = when (settings.surfaceFinish) {
            AppSettings.SurfaceFinish.CLAY -> Finish.CLAY
            AppSettings.SurfaceFinish.MACHINED -> Finish.MACHINED
        }
        _revision.value = _revision.value + 1
    }

    /**
     * The finish selects the palette, the mode selects light or dark.
     *
     * Clay carries its own warm neutrals on purpose: the machined palette is biased cold
     * (graphite + petrol), and pressed matter under a cold light reads as grey plastic.
     */
    fun themeRes(context: Context, settings: AppSettings): Int {
        val dark = when (settings.themeMode) {
            AppSettings.ThemeMode.DARK -> true
            AppSettings.ThemeMode.LIGHT -> false
            AppSettings.ThemeMode.SYSTEM -> isSystemNight(context)
        }
        return when (settings.surfaceFinish) {
            AppSettings.SurfaceFinish.CLAY ->
                if (dark) R.style.Theme_DynabloxL_Clay else R.style.Theme_DynabloxL_Clay_Light
            AppSettings.SurfaceFinish.MACHINED ->
                if (dark) R.style.Theme_DynabloxL else R.style.Theme_DynabloxL_Light
        }
    }

    /**
     * Identity of the currently mounted theme.
     *
     * Overlay views are built against a [ContextThemeWrapper] captured at construction time, and a
     * wrapper cannot be re-pointed at another style afterwards. So `refreshTheme()` alone is not
     * enough when the *style resource itself* changes (dark <-> light, clay <-> machined): the
     * view would keep resolving tokens from the theme it was born with. Services compare this
     * signature and rebuild their window when it moves, and only re-tint when it does not.
     */
    fun signature(context: Context, settings: AppSettings): Int = themeRes(context, settings)

    fun isSystemNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    /** Themed context used by overlay views created from a Service. */
    fun wrap(context: Context, settings: AppSettings): Context =
        ContextThemeWrapper(context.applicationContext, themeRes(context, settings))

    fun tokens(context: Context, settings: AppSettings): ThemeTokens =
        ThemeTokens.from(wrap(context, settings))

    /** Convenience for views that only know their own (already themed) context. */
    fun tokens(context: Context): ThemeTokens = ThemeTokens.from(context)
}

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

    /** Bumped whenever the theme, scale or motion settings change. */
    val revision: StateFlow<Int> = _revision

    /** Pushes settings into the global motion/dimension helpers. Call on every settings change. */
    fun sync(settings: AppSettings) {
        Motion.apply(settings)
        Dimens.apply(settings)
        shadowIntensity = settings.shadowIntensity
        translucent = settings.translucentSurfaces
        _revision.value = _revision.value + 1
    }

    fun themeRes(context: Context, settings: AppSettings): Int = when (settings.themeMode) {
        AppSettings.ThemeMode.LIGHT -> R.style.Theme_DynabloxL_Light
        AppSettings.ThemeMode.DARK -> R.style.Theme_DynabloxL
        AppSettings.ThemeMode.SYSTEM -> if (isSystemNight(context)) {
            R.style.Theme_DynabloxL
        } else {
            R.style.Theme_DynabloxL_Light
        }
    }

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

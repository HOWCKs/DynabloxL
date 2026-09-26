package com.dynablox.launcher

import android.app.Application
import android.content.Context
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.di.AppContainer

/**
 * Application entry point.
 *
 * Owns the single [AppContainer] for the process — overlay services, widgets and activities all
 * resolve their collaborators through `DynabloxApp.of(context).container`, which is what keeps the
 * floating UI and the in-app UI looking at the same settings, telemetry and Shizuku session.
 */
class DynabloxApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    val settings: AppSettings get() = container.settings

    override fun onCreate() {
        super.onCreate()
        container.init()
    }

    override fun onTerminate() {
        container.shutdown()
        super.onTerminate()
    }

    companion object {
        fun of(context: Context): DynabloxApp = context.applicationContext as DynabloxApp

        fun containerOf(context: Context): AppContainer = of(context).container
    }
}

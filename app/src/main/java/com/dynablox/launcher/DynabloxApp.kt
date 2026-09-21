package com.dynablox.launcher

import android.app.Application
import android.content.Context
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.design.SkeuoTheme

class DynabloxApp : Application() {
    lateinit var settings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        SkeuoTheme.sync(settings)
    }

    companion object {
        fun of(context: Context): DynabloxApp = context.applicationContext as DynabloxApp
    }
}

package com.dynablox.launcher.ui

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dynablox.launcher.DynabloxApp
import com.dynablox.launcher.R
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.widgets.SkeuoPanel

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(SkeuoTheme.themeRes(this, DynabloxApp.of(this).settings))
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val panel = SkeuoPanel(this)
        val text = TextView(this, null, 0, R.style.Dbx_Text_Title)
        text.setText(R.string.app_tagline)
        panel.addView(text)
        root.addView(panel)
        setContentView(root)
    }
}

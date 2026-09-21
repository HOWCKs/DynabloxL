package com.dynablox.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dynablox.launcher.DynabloxApp
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.ThemeTokens
import com.dynablox.launcher.design.widgets.FaderSlider
import com.dynablox.launcher.design.widgets.LedIndicator
import com.dynablox.launcher.design.widgets.PhysicalButton
import com.dynablox.launcher.design.widgets.SkeuoPanel
import com.dynablox.launcher.design.widgets.ToggleSwitch
import com.dynablox.launcher.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Shared chrome for every in-app screen: brushed header rail, scrolling card stack and a set of
 * builders that turn a setting into a physical control (toggle, fader, segmented key, LED readout).
 *
 * Screens are assembled in code so they inherit the same design system — and the same runtime
 * re-tinting — as the overlays, with no duplicated XML to drift out of sync.
 */
abstract class DbxActivity : AppCompatActivity(), CommandFeedback {

    protected lateinit var container: AppContainer
    protected lateinit var settings: AppSettings
    protected lateinit var contentRoot: LinearLayout

    private var scrollView: ScrollView? = null
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private var designSignature = ""

    protected val tokens: ThemeTokens get() = SkeuoTheme.tokens(this)

    @get:StringRes
    protected abstract val titleRes: Int

    protected open val subtitleRes: Int? = null

    @DrawableRes
    protected open val headerIconRes: Int? = null

    /**
     * False for screens that host a SurfaceView (Shader Lab): a GL surface inside a ScrollView is
     * clipped and z-ordered incorrectly, so those screens lay out their content directly.
     */
    protected open val scrollsContent: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        container = DynabloxApp.containerOf(this)
        settings = container.settings
        setTheme(SkeuoTheme.themeRes(this, settings))
        super.onCreate(savedInstanceState)
        designSignature = signature()
        setContentView(buildScaffold())
        buildContent(contentRoot)
        observeSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    /** Rebuilds the screen — call it after a permission or state change that alters the copy. */
    protected fun refreshContent() {
        contentRoot.removeAllViews()
        buildContent(contentRoot)
    }

    protected abstract fun buildContent(root: LinearLayout)

    // ------------------------------------------------------------------ scaffold

    private fun buildScaffold(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.surfaceBase)
        }
        root.addView(buildHeader(), headerParams())

        contentRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (scrollsContent) {
            val scroll = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                val pad = dp(14f).toInt()
                setPadding(pad, dp(12f).toInt(), pad, dp(28f).toInt())
            }
            scroll.addView(
                contentRoot,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            scrollView = scroll
            root.addView(
                scroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        } else {
            val pad = dp(14f).toInt()
            contentRoot.setPadding(pad, dp(12f).toInt(), pad, dp(20f).toInt())
            root.addView(
                contentRoot,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        return root
    }

    private fun buildHeader(): View {
        val header = SkeuoPanel(this).apply {
            material = Material.METAL
            cornerRadius = 0f
            options = MaterialOptions(specular = true, shadow = 0.6f)
            val pad = dp(14f).toInt()
            setPadding(pad, dp(12f).toInt(), pad, dp(12f).toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = PhysicalButton(this).apply {
            shape = PhysicalButton.Shape.ROUND
            setIconResource(R.drawable.ic_chevron)
            rotation = 180f
            contentDescription = getString(R.string.cd_back)
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44f).toInt(), dp(44f).toInt()))

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val start = dp(12f).toInt()
            setPadding(start, 0, start, 0)
        }
        titleView = TextView(this, null, 0, R.style.Dbx_Text_Title).apply {
            text = getString(titleRes)
        }
        textBlock.addView(titleView)
        subtitleView = TextView(this, null, 0, R.style.Dbx_Text_Caption).apply {
            text = subtitleRes?.let { getString(it) } ?: getString(R.string.app_tagline)
        }
        textBlock.addView(subtitleView)
        row.addView(
            textBlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        headerIconRes?.let { icon ->
            val mark = PhysicalButton(this).apply {
                shape = PhysicalButton.Shape.ROUND
                setIconResource(icon)
                isEnabled = false
                contentDescription = getString(titleRes)
            }
            row.addView(mark, LinearLayout.LayoutParams(dp(44f).toInt(), dp(44f).toInt()))
        }

        header.addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return header
    }

    private fun headerParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun observeSettings() {
        lifecycleScope.launch {
            settings.changes.collect {
                val next = signature()
                if (next != designSignature) {
                    designSignature = next
                    recreate()
                }
            }
        }
    }

    private fun signature(): String = listOf(
        settings.themeMode, settings.densityScale, settings.translucentSurfaces,
        settings.shadowIntensity, settings.reduceMotion, settings.animationStyle,
    ).joinToString("|")

    // ------------------------------------------------------------------ builders

    protected fun dp(value: Float): Float = Dimens.dp(this, value)

    protected fun section(@StringRes titleRes: Int, @StringRes captionRes: Int? = null) {
        val label = TextView(this, null, 0, R.style.Dbx_Text_Section).apply {
            text = getString(titleRes)
        }
        contentRoot.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18f).toInt(); bottomMargin = dp(6f).toInt() },
        )
        if (captionRes != null) {
            val caption = TextView(this, null, 0, R.style.Dbx_Text_Caption).apply {
                text = getString(captionRes)
            }
            contentRoot.addView(
                caption,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8f).toInt() },
            )
        }
    }

    /** A raised card; [body] fills it with rows. */
    protected fun card(body: LinearLayout.() -> Unit): LinearLayout {
        val panel = SkeuoPanel(this).apply {
            material = if (SkeuoTheme.translucent) Material.GLASS else Material.CERAMIC
            cornerRadius = dp(20f)
            options = MaterialOptions(shadow = 1f)
            val pad = dp(14f).toInt()
            setPadding(pad, dp(12f).toInt(), pad, dp(12f).toInt())
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(
            inner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        contentRoot.addView(
            panel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(4f).toInt() },
        )
        inner.body()
        return inner
    }

    protected fun LinearLayout.divider() {
        val line = View(context).apply { setBackgroundColor(tokens.divider) }
        addView(
            line,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = dp(10f).toInt()
                bottomMargin = dp(10f).toInt()
            },
        )
    }

    protected fun LinearLayout.textRow(
        @StringRes labelRes: Int,
        value: String,
        valueColor: Int? = null,
    ): TextView = textRowText(getString(labelRes), value, valueColor)

    /** [textRow] with runtime strings, for entries whose copy comes from data. */
    protected fun LinearLayout.textRowText(
        label: String,
        value: String,
        valueColor: Int? = null,
    ): TextView {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            TextView(context, null, 0, R.style.Dbx_Text_Body).apply { text = label },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val valueView = TextView(context, null, 0, R.style.Dbx_Text_Mono).apply {
            text = value
            setTextColor(valueColor ?: tokens.textSecondary)
            gravity = Gravity.END
        }
        row.addView(
            valueView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(row, wrapVertical(top = dp(4f)))
        return valueView
    }

    protected fun LinearLayout.toggleRow(
        @StringRes labelRes: Int,
        @StringRes descriptionRes: Int?,
        checked: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ): ToggleSwitch = toggleRowText(
        getString(labelRes),
        descriptionRes?.let { getString(it) },
        checked,
        enabled,
        onChange,
    )

    /** [toggleRow] with runtime strings. */
    protected fun LinearLayout.toggleRowText(
        label: String,
        description: String?,
        checked: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ): ToggleSwitch {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textBlock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(
            TextView(context, null, 0, R.style.Dbx_Text_Body).apply { text = label },
        )
        if (description != null) {
            textBlock.addView(
                TextView(context, null, 0, R.style.Dbx_Text_Caption).apply {
                    text = description
                    alpha = if (enabled) 1f else 0.5f
                },
            )
        }
        row.addView(textBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = ToggleSwitch(context).apply {
            setChecked(checked)
            isEnabled = enabled
            a11yLabel = label
            onCheckedChanged = { value -> onChange(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(58f).toInt(), dp(34f).toInt()))
        addView(row, wrapVertical(top = dp(8f)))
        return toggle
    }

    protected fun LinearLayout.faderRow(
        @StringRes labelRes: Int,
        min: Float,
        max: Float,
        value: Float,
        unit: String? = null,
        onChange: (Float) -> Unit,
    ): FaderSlider {
        val fader = FaderSlider(context).apply {
            minValue = min
            maxValue = max
            this.value = value
            labelText = getString(labelRes)
            this.unit = unit
            onValueChanged = { v -> onChange(v) }
        }
        addView(fader, wrapVertical(top = dp(10f)).apply { height = dp(64f).toInt() })
        return fader
    }

    protected fun LinearLayout.segmentedRow(
        @StringRes labelRes: Int,
        options: List<SegmentOption>,
    ) {
        addView(
            TextView(context, null, 0, R.style.Dbx_Text_Body).apply { text = getString(labelRes) },
            wrapVertical(top = dp(10f)),
        )
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        options.forEach { option ->
            val button = PhysicalButton(context).apply {
                label = option.label
                shape = PhysicalButton.Shape.CAPSULE
                isActivated = option.selected()
                setOnClickListener {
                    option.onPick()
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
        addView(row, wrapVertical(top = dp(6f)))
    }

    protected data class SegmentOption(
        val label: String,
        val selected: () -> Boolean,
        val onPick: () -> Unit,
    )

    protected fun LinearLayout.buttonRow(
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int? = null,
        @StringRes descriptionRes: Int? = null,
        enabled: Boolean = true,
        accent: Int? = null,
        onClick: () -> Unit,
    ): PhysicalButton {
        val button = PhysicalButton(context).apply {
            label = getString(labelRes)
            descriptionRes?.let { subLabel = getString(it) }
            iconRes?.let { setIconResource(it) }
            isEnabled = enabled
            accentOverride = accent
            shape = PhysicalButton.Shape.CAPSULE
            setOnClickListener { onClick() }
        }
        addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10f).toInt() },
        )
        return button
    }

    /** Same chrome as [buttonRow] but with runtime strings — used for app and experience entries. */
    protected fun LinearLayout.actionRow(
        label: String,
        subLabel: String? = null,
        icon: android.graphics.drawable.Drawable? = null,
        @DrawableRes iconRes: Int? = null,
        accent: Int? = null,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): PhysicalButton {
        val button = PhysicalButton(context).apply {
            this.label = label
            this.subLabel = subLabel
            icon?.let { this.icon = it }
            iconRes?.let { setIconResource(it) }
            accentOverride = accent
            shape = PhysicalButton.Shape.CAPSULE
            contentDescription = label
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.55f
            setOnClickListener { onClick() }
        }
        addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8f).toInt() },
        )
        return button
    }

    protected fun LinearLayout.ledRow(label: String, lit: Boolean, color: Int? = null): LedIndicator {
        val led = LedIndicator(context).apply {
            labelText = label
            this.lit = lit
            colorOverride = color
        }
        addView(led, wrapVertical(top = dp(6f)).apply { height = dp(28f).toInt() })
        return led
    }

    protected fun LinearLayout.paragraph(@StringRes textRes: Int) {
        paragraphText(getString(textRes))
    }

    protected fun LinearLayout.paragraphText(text: String) {
        addView(
            TextView(context, null, 0, R.style.Dbx_Text_Caption).apply { this.text = text },
            wrapVertical(top = dp(6f)),
        )
    }

    private fun wrapVertical(top: Float = 0f) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = top.toInt() }

    // ------------------------------------------------------------------ helpers

    protected fun open(target: Class<*>, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(this, target)
        extras?.invoke(intent)
        startActivity(intent)
    }

    override fun show(message: String, kind: CommandFeedback.Kind) {
        val length = if (message.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(this, message, length).show()
    }

    protected fun launchSafely(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                show(getString(R.string.err_generic, t.message ?: "error"), CommandFeedback.Kind.ERROR)
            }
        }
    }

    protected fun scrollToEnd() {
        val target = scrollView ?: return
        target.post { target.fullScroll(View.FOCUS_DOWN) }
    }
}

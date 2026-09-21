package com.dynablox.launcher.menu

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dynablox.launcher.R
import com.dynablox.launcher.core.commands.Command
import com.dynablox.launcher.core.commands.CommandCategory
import com.dynablox.launcher.core.commands.CommandContext
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.Tint
import com.dynablox.launcher.design.widgets.IconTile
import com.dynablox.launcher.design.widgets.LedIndicator
import com.dynablox.launcher.design.widgets.PhysicalButton
import com.dynablox.launcher.design.widgets.SearchField
import com.dynablox.launcher.design.widgets.SkeuoPanel
import com.dynablox.launcher.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The modular half of the MenuHub: search, category chips, a command grid and a live status footer.
 *
 * Built programmatically (not from XML) because it lives inside an overlay window that has to be
 * re-tinted at runtime without an Activity to recreate.
 */
class HubPanel(
    context: Context,
    private val container: AppContainer,
) : SkeuoPanel(context), CommandFeedback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = container.settings

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = SkeuoTheme.tokens(context).let { dp(14f).toInt() }
        setPadding(pad, dp(12f).toInt(), pad, dp(12f).toInt())
    }

    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val title = TextView(context).apply {
        text = context.getString(R.string.app_name)
        setTextColor(tokens().textPrimary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 16f
        letterSpacing = 0.02f
    }

    private val subtitle = TextView(context).apply {
        text = context.getString(R.string.app_tagline)
        setTextColor(tokens().textFaint)
        textSize = 10f
        letterSpacing = 0.14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = true
    }

    private val closeButton = PhysicalButton(context).apply {
        shape = PhysicalButton.Shape.ROUND
        setIconResource(R.drawable.ic_close)
        contentDescription = context.getString(R.string.cd_close_panel)
        minimumWidth = dp(40f).toInt()
        minimumHeight = dp(40f).toInt()
    }

    private val search = SearchField(context).apply {
        onClear = { applyFilter() }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
    }

    private val chipsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
    }
    private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    private val gridScroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
    private val grid = GridLayout(context).apply { columnCount = 4 }

    private val emptyState = TextView(context).apply {
        text = context.getString(R.string.state_empty)
        setTextColor(tokens().textFaint)
        gravity = Gravity.CENTER
        textSize = 12f
        visibility = View.GONE
        setPadding(0, dp(18f).toInt(), 0, dp(18f).toInt())
    }

    private val statusRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val overlayLed = LedIndicator(context).apply { labelText = "overlay" }
    private val shizukuLed = LedIndicator(context).apply { labelText = "shizuku" }
    private val hudLed = LedIndicator(context).apply { labelText = "hud" }
    private val padLed = LedIndicator(context).apply { labelText = "pad" }

    private val feedbackPill = TextView(context).apply {
        textSize = 11f
        setTextColor(tokens().textSecondary)
        visibility = View.GONE
        maxLines = 2
    }

    private var activeCategory: CommandCategory? = null
    private var onCommand: ((Command) -> Unit)? = null

    init {
        material = if (SkeuoTheme.translucent) Material.GLASS else Material.CERAMIC
        cornerRadius = dp(24f)
        options = MaterialOptions(shadow = 1f)
        elevation = dp(14f) * SkeuoTheme.shadowIntensity

        buildHeader()
        root.addView(header, verticalParams())
        if (settings.hubShowSearch) {
            root.addView(search, verticalParams(top = dp(10f)))
        }
        root.addView(chipsScroll, verticalParams(top = dp(10f)))
        root.addView(gridScroll, verticalParams(top = dp(10f)).apply { height = dp(292f).toInt() })
        root.addView(emptyState, verticalParams())
        root.addView(feedbackPill, verticalParams(top = dp(8f)))
        root.addView(statusRow, verticalParams(top = dp(10f)))
        addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        closeButton.setOnClickListener { onCommand?.invoke(CLOSE_COMMAND) }
        chipsScroll.addView(chips)
        gridScroll.addView(grid)
        listOf(overlayLed, shizukuLed, hudLed, padLed).forEach { led ->
            statusRow.addView(led, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        rebuildChips()
        applyFilter()
        refreshStatus()
    }

    fun setCommandHandler(handler: (Command) -> Unit) {
        onCommand = handler
    }

    fun focusSearch() {
        if (!settings.hubShowSearch) return
        search.requestFocus()
    }

    fun clearSearch() {
        if (search.text?.isNotEmpty() == true) {
            search.setText("")
            applyFilter()
        }
    }

    private fun buildHeader() {
        val titleBlock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(title)
        titleBlock.addView(subtitle)
        header.addView(
            titleBlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(closeButton, LinearLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt()))
    }

    private fun rebuildChips() {
        chips.removeAllViews()
        val categories = container.commands.categories(settings)
        val all = PhysicalButton(context).apply {
            label = context.getString(R.string.menuhub_all)
            shape = PhysicalButton.Shape.CAPSULE
            isActivated = activeCategory == null
            setOnClickListener {
                activeCategory = null
                rebuildChips()
                applyFilter()
            }
        }
        chips.addView(all, chipParams())
        categories.forEach { category ->
            val chip = PhysicalButton(context).apply {
                label = context.getString(category.labelRes)
                setIconResource(category.iconRes)
                shape = PhysicalButton.Shape.CAPSULE
                isActivated = activeCategory == category
                setOnClickListener {
                    activeCategory = if (activeCategory == category) null else category
                    rebuildChips()
                    applyFilter()
                }
            }
            chips.addView(chip, chipParams())
        }
    }

    private fun applyFilter() {
        val query = search.text?.toString() ?: ""
        val commands = if (query.isBlank()) {
            if (activeCategory == null) {
                container.commands.visibleCommands(settings)
            } else {
                container.commands.byCategory(activeCategory as CommandCategory, settings)
            }
        } else {
            container.commands.search(query, settings)
        }
        renderGrid(commands, query)
    }

    private fun renderGrid(commands: List<Command>, query: String) {
        grid.removeAllViews()
        val favorites = settings.hubFavorites
        val limit = if (query.isBlank()) MAX_TILES else MAX_RESULTS
        commands.take(limit).forEach { command ->
            val tile = IconTile(context).apply {
                iconRes = command.iconRes
                label = context.getString(command.titleRes)
                contentDescription = context.getString(command.titleRes)
                favorite = command.id in favorites && command.pinnable
                accentOverride = tintFor(command)
                isActivated = isRunning(command)
                setOnClickListener { onCommand?.invoke(command) }
                setOnLongClickListener {
                    if (command.pinnable) {
                        settings.toggleFavorite(command.id)
                        show(
                            if (command.id in settings.hubFavorites) {
                                context.getString(R.string.menuhub_pinned)
                            } else {
                                context.getString(R.string.menuhub_unpinned)
                            },
                            CommandFeedback.Kind.SUCCESS,
                        )
                        applyFilter()
                    }
                    true
                }
            }
            val lp = GridLayout.LayoutParams()
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(0, dp(3f).toInt(), 0, dp(3f).toInt())
            grid.addView(tile, lp)
        }
        emptyState.visibility = if (commands.isEmpty()) View.VISIBLE else View.GONE
        emptyState.text = if (query.isBlank()) {
            context.getString(R.string.state_empty)
        } else {
            context.getString(R.string.menuhub_search_empty, query)
        }
    }

    fun refreshStatus() {
        overlayLed.lit = container.windows.canDrawOverlays()
        overlayLed.colorOverride = if (overlayLed.lit) tokens().success else tokens().warning
        shizukuLed.lit = container.shizuku.isGranted
        shizukuLed.colorOverride = if (shizukuLed.lit) tokens().accent else tokens().warning
        hudLed.lit = container.overlays.isHudRunning
        hudLed.colorOverride = tokens().accent
        padLed.lit = container.overlays.isControlsRunning
        padLed.colorOverride = tokens().warning
    }

    private fun isRunning(command: Command): Boolean = when (command.id) {
        "overlay.hud" -> container.overlays.isHudRunning
        "visual.fx" -> container.overlays.isFxRunning
        "input.pad" -> container.overlays.isControlsRunning
        else -> false
    }

    private fun tintFor(command: Command): Int = when (command.tint) {
        com.dynablox.launcher.core.commands.CommandTint.ACCENT -> tokens().accent
        com.dynablox.launcher.core.commands.CommandTint.SUCCESS -> tokens().success
        com.dynablox.launcher.core.commands.CommandTint.WARNING -> tokens().warning
        com.dynablox.launcher.core.commands.CommandTint.DANGER -> tokens().danger
        else -> tokens().iconTint
    }

    /** Runs a command on the panel's scope and routes its feedback to the inline pill. */
    fun execute(command: Command) {
        scope.launch {
            try {
                command.action(CommandContext(this@HubPanel.context, container, this@HubPanel))
            } catch (t: Throwable) {
                show(this@HubPanel.context.getString(R.string.err_generic, t.message ?: "error"), CommandFeedback.Kind.ERROR)
            }
            refreshStatus()
        }
    }

    override fun show(message: String, kind: CommandFeedback.Kind) {
        feedbackPill.text = message
        feedbackPill.visibility = View.VISIBLE
        feedbackPill.setTextColor(
            when (kind) {
                CommandFeedback.Kind.SUCCESS -> tokens().success
                CommandFeedback.Kind.WARNING -> tokens().warning
                CommandFeedback.Kind.ERROR -> tokens().danger
                CommandFeedback.Kind.INFO -> tokens().textSecondary
            },
        )
        feedbackPill.setBackgroundColor(Tint.alphaFraction(Color.BLACK, 0.0f))
        feedbackPill.removeCallbacks(hideFeedback)
        feedbackPill.postDelayed(hideFeedback, 3200L)
    }

    private val hideFeedback = Runnable {
        feedbackPill.visibility = View.GONE
    }

    fun release() {
        feedbackPill.removeCallbacks(hideFeedback)
        scope.cancel()
    }

    override fun refreshTheme() {
        super.refreshTheme()
        material = if (SkeuoTheme.translucent) Material.GLASS else Material.CERAMIC
        elevation = dp(14f) * SkeuoTheme.shadowIntensity
        title.setTextColor(tokens().textPrimary)
        subtitle.setTextColor(tokens().textFaint)
        emptyState.setTextColor(tokens().textFaint)
        feedbackPill.setTextColor(tokens().textSecondary)
        rebuildChips()
        applyFilter()
        refreshStatus()
    }

    private fun tokens() = SkeuoTheme.tokens(context)

    private fun dp(value: Float): Float = com.dynablox.launcher.design.Dimens.dp(context, value)

    private fun verticalParams(top: Float = 0f) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = top.toInt() }

    private fun chipParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = dp(6f).toInt() }

    companion object {
        private const val MAX_TILES = 16
        private const val MAX_RESULTS = 24

        /** Pseudo-command used by the panel's own close key. */
        val CLOSE_COMMAND = Command(
            id = "panel.close",
            titleRes = R.string.action_close,
            descriptionRes = R.string.menuhub_close,
            iconRes = R.drawable.ic_close,
            category = CommandCategory.SYSTEM,
        )
    }
}

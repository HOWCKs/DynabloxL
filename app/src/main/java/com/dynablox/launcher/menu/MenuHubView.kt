package com.dynablox.launcher.menu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.dynablox.launcher.R
import com.dynablox.launcher.core.commands.Command
import com.dynablox.launcher.core.commands.CommandContext
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.overlay.ScreenMetrics
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.Motion
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.widgets.IconTile
import com.dynablox.launcher.design.widgets.OrbView
import com.dynablox.launcher.design.widgets.RadialArc
import com.dynablox.launcher.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * The whole floating hub: orb + radial arc + modular panel.
 *
 * Three states in one view:
 *  - [State.COLLAPSED] — just the knob, resting over the desktop.
 *  - [State.ARC] — primary commands unfold around the knob on a machined hinge.
 *  - [State.PANEL] — the search / grid module drops out under the knob.
 *
 * The host service reads [geometry] to size and position the window, then calls [bindGeometry]; this
 * view lays its children out in exactly that geometry, so taps outside the visible surface fall
 * through to whatever is underneath and come back as ACTION_OUTSIDE.
 */
class MenuHubView(
    context: Context,
    private val container: AppContainer,
) : FrameLayout(SkeuoTheme.wrap(context, container.settings)), CommandFeedback {

    enum class State { COLLAPSED, ARC, PANEL }

    /** Window rect (size) plus where the orb sits inside it. */
    data class Geometry(val width: Int, val height: Int, val orbX: Int, val orbY: Int)

    private val settings = container.settings
    private val themed = SkeuoTheme.wrap(context, settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val orb: OrbView = OrbView(themed)
    private val arc: RadialArc = RadialArc(themed)
    private val panel: HubPanel = HubPanel(themed, container)

    var state: State = State.COLLAPSED
        private set

    var onStateChange: ((State) -> Unit)? = null
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var geometry = Geometry(0, 0, 0, 0)
    private var arcRadiusPx = 0f
    private var expandAnimator: ValueAnimator? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        clipChildren = false
        clipToPadding = false

        addView(arc, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(orb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        panel.setCommandHandler { command -> handleCommand(command) }
        orb.setOnClickListener { toggle() }
        orb.setOnLongClickListener {
            container.requestOverlayPermission()
            true
        }
        orb.setOnTouchListener { _, event -> handleOrbTouch(event) }
        orb.tooltip = context.getString(R.string.menuhub_open)

        rebuildArc()
        applyState(State.COLLAPSED, animate = false)
    }

    // ------------------------------------------------------------------ sizing

    private fun dp(value: Float): Float = Dimens.dp(context, value)

    private fun orbSizePx(): Int {
        val base = settings.orbSizeDp * resources.displayMetrics.density * Dimens.factor
        return base.toInt().coerceIn(dp(44f).toInt(), dp(128f).toInt())
    }

    private fun tilePx(): Int = dp(62f).toInt()

    private fun glowMargin(): Int = dp(16f).toInt()

    /** Window rect for the current state, measured against the real screen. */
    fun computeGeometry(screen: ScreenMetrics): Geometry {
        val size = orbSizePx()
        val margin = glowMargin()
        return when (state) {
            State.COLLAPSED -> {
                val side = size + margin * 2
                Geometry(side, side, side / 2, side / 2)
            }

            State.ARC -> {
                arcRadiusPx = size / 2f + dp(18f) + tilePx() / 2f
                val half = (arcRadiusPx + tilePx() * 0.62f + margin).toInt()
                val side = half * 2
                val capped = min(side, min(screen.widthPx, screen.usableHeight))
                Geometry(capped, capped, capped / 2, capped / 2)
            }

            State.PANEL -> {
                val maxWidth = min(dp(360f).toInt(), (screen.widthPx * 0.94f).toInt())
                val maxHeight = (screen.usableHeight * 0.86f).toInt()
                panel.measure(
                    View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                )
                val panelWidth = max(panel.measuredWidth, dp(280f).toInt())
                val panelHeight = min(panel.measuredHeight, maxHeight)
                val gap = dp(4f).toInt()
                Geometry(
                    panelWidth,
                    margin + size + gap + panelHeight,
                    panelWidth / 2,
                    margin + size / 2,
                )
            }
        }
    }

    fun bindGeometry(geo: Geometry) {
        geometry = geo
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val size = orbSizePx()
        val cx = if (geometry.width == 0) width / 2 else geometry.orbX
        val cy = if (geometry.width == 0) size / 2 + glowMargin() else geometry.orbY

        orb.layout(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        when (state) {
            State.COLLAPSED -> {
                arc.layout(0, 0, 0, 0)
                panel.layout(0, 0, 0, 0)
            }

            State.ARC -> {
                arc.layout(0, 0, width, height)
                arc.radius = arcRadiusPx
                arc.centerAngle = arcDirection(cx, cy)
                panel.layout(0, 0, 0, 0)
                orb.bringToFront()
            }

            State.PANEL -> {
                arc.layout(0, 0, 0, 0)
                val panelWidth = panel.measuredWidth
                val panelHeight = panel.measuredHeight
                val panelLeft = ((width - panelWidth) / 2f).toInt()
                val panelTop = (cy + size / 2 - dp(8f)).toInt().coerceIn(0, max(0, height - panelHeight))
                panel.layout(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)
                panel.bringToFront()
                orb.bringToFront()
            }
        }
    }

    /** Points the arc towards the free space around the orb, so satellites never leave the screen. */
    private fun arcDirection(orbX: Int, orbY: Int): Float {
        val screen = container.windows.metrics()
        val windowLeft = (screen.widthPx - width) / 2f
        val dx = screen.widthPx / 2f - (windowLeft + orbX)
        val dy = screen.usableHeight / 2f - orbY
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        // Fall back to "up" when the orb sits dead centre.
        return if (abs(dx) < dp(24f) && abs(dy) < dp(24f)) 270f else angle
    }

    // ------------------------------------------------------------------ states

    fun toggle() {
        when (state) {
            State.COLLAPSED -> applyState(State.ARC, animate = true)
            State.ARC -> applyState(State.COLLAPSED, animate = true)
            State.PANEL -> applyState(State.ARC, animate = true)
        }
    }

    fun openPanel() = applyState(State.PANEL, animate = true)

    fun collapse() = applyState(State.COLLAPSED, animate = true)

    fun expand() = applyState(State.ARC, animate = true)

    fun applyState(next: State, animate: Boolean) {
        val previous = state
        state = next
        orb.expanded = next != State.COLLAPSED
        panel.visibility = if (next == State.PANEL) View.VISIBLE else View.INVISIBLE
        arc.visibility = if (next == State.ARC) View.VISIBLE else View.INVISIBLE
        if (next == State.PANEL) {
            panel.refreshStatus()
            panel.clearSearch()
        }
        expandAnimator?.cancel()
        if (!animate || Motion.duration(160L) == 0L) {
            arc.progress = if (next == State.ARC) 1f else 0f
            panel.alpha = if (next == State.PANEL) 1f else 0f
            requestLayout()
            invalidate()
        } else {
            expandAnimator = ValueAnimator.ofFloat(arc.progress, if (next == State.ARC) 1f else 0f).apply {
                duration = Motion.duration(260L)
                interpolator = Motion.expandInterpolator()
                addUpdateListener {
                    arc.progress = it.animatedValue as Float
                    val value = it.animatedValue as Float
                    panel.alpha = if (next == State.PANEL) value else 1f - value
                    invalidate()
                }
                start()
            }
        }
        if (previous != next) onStateChange?.invoke(next)
    }

    fun rebuildArc() {
        arc.removeAllViews()
        val tile = tilePx()
        val favorites = settings.hubFavorites
        val commands = container.commands.radialCommands(settings, limit = 7)
        commands.forEach { command ->
            arc.addView(arcTile(command, tile, favorites.contains(command.id)))
        }
        val more = IconTile(themed).apply {
            iconRes = R.drawable.ic_grid
            label = context.getString(R.string.menuhub_all)
            contentDescription = context.getString(R.string.menuhub_all)
            material = Material.METAL
            setOnClickListener { openPanel() }
        }
        arc.addView(more, FrameLayout.LayoutParams(tile, tile + dp(20f).toInt()))
        arcRadiusPx = orbSizePx() / 2f + dp(18f) + tile / 2f
        requestLayout()
    }

    private fun arcTile(command: Command, tile: Int, favorite: Boolean): IconTile =
        IconTile(themed).apply {
            iconRes = command.iconRes
            label = context.getString(command.titleRes)
            this.favorite = favorite
            contentDescription = context.getString(command.titleRes)
            accentOverride = tintFor(command)
            isActivated = isRunning(command.id)
            setOnClickListener { handleCommand(command) }
            setOnLongClickListener {
                show(command.description(context), CommandFeedback.Kind.INFO)
                true
            }
        }.also { it.layoutParams = FrameLayout.LayoutParams(tile, tile + dp(20f).toInt()) }

    private fun handleCommand(command: Command) {
        if (command.id == HubPanel.CLOSE_COMMAND.id) {
            collapse()
            return
        }
        val feedback: CommandFeedback = if (state == State.PANEL) panel else this
        scope.launch {
            try {
                command.action(CommandContext(this@MenuHubView.context, container, feedback))
            } catch (t: Throwable) {
                feedback.show(this@MenuHubView.getString(R.string.err_generic, t.message ?: "error"), CommandFeedback.Kind.ERROR)
            }
            refreshAfterCommand(command)
        }
        if (command.id == "overlay.stopAll" || command.id == "overlay.hub.hide") {
            onDismiss?.invoke()
            return
        }
        if (command.id.startsWith("roblox.")) collapse()
    }

    private fun refreshAfterCommand(command: Command) {
        panel.refreshStatus()
        if (command.id.startsWith("overlay.") || command.id.startsWith("visual.") ||
            command.id.startsWith("input.") || command.id == "system.theme"
        ) {
            rebuildArc()
        }
    }

    private fun isRunning(id: String): Boolean = when (id) {
        "overlay.hud" -> container.overlays.isHudRunning
        "visual.fx" -> container.overlays.isFxRunning
        "input.pad" -> container.overlays.isControlsRunning
        else -> false
    }

    private fun tintFor(command: Command): Int? = when (command.tint) {
        com.dynablox.launcher.core.commands.CommandTint.ACCENT -> tokens().accent
        com.dynablox.launcher.core.commands.CommandTint.SUCCESS -> tokens().success
        com.dynablox.launcher.core.commands.CommandTint.WARNING -> tokens().warning
        com.dynablox.launcher.core.commands.CommandTint.DANGER -> tokens().danger
        else -> null
    }

    private fun tokens() = SkeuoTheme.tokens(context)

    // ------------------------------------------------------------------ orb dragging

    private fun handleOrbTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                if (!dragging && (abs(dx) > touchSlop * 1.5f || abs(dy) > touchSlop * 1.5f)) {
                    dragging = true
                    orb.setPressedState(false)
                }
                if (dragging) {
                    onDrag?.invoke(dx.toInt(), dy.toInt())
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd?.invoke()
                    return true
                }
                dragging = false
            }
        }
        return false
    }

    /** Outside taps reach us through FLAG_WATCH_OUTSIDE_TOUCH. */
    fun handleOutsideTouch() {
        if (state != State.COLLAPSED) collapse()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (state == State.PANEL) applyState(State.ARC, animate = true) else collapse()
                    return true
                }

                KeyEvent.KEYCODE_SEARCH -> {
                    openPanel()
                    panel.focusSearch()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------------ lifecycle

    override fun show(message: String, kind: CommandFeedback.Kind) {
        if (state == State.PANEL) {
            panel.show(message, kind)
        } else {
            container.toastFeedback.show(message, kind)
        }
    }

    fun refreshTheme() {
        orb.refreshTheme()
        arc.refreshTheme()
        panel.refreshTheme()
        rebuildArc()
        requestLayout()
        invalidate()
    }

    fun release() {
        expandAnimator?.cancel()
        panel.release()
        scope.cancel()
    }

    /** Screen position of the orb centre, given the window's top-left corner. */
    fun orbScreenCenter(windowLeft: Int, windowTop: Int): Point =
        Point(windowLeft + geometry.orbX, windowTop + geometry.orbY)
}

package com.dynablox.launcher.hud

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dynablox.launcher.R
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.commands.CommandFeedback
import com.dynablox.launcher.core.perf.FpsSourceKind
import com.dynablox.launcher.core.perf.PerfSnapshot
import com.dynablox.launcher.core.util.Fmt
import com.dynablox.launcher.design.Dimens
import com.dynablox.launcher.design.Material
import com.dynablox.launcher.design.MaterialOptions
import com.dynablox.launcher.design.SkeuoTheme
import com.dynablox.launcher.design.widgets.DigitalReadout
import com.dynablox.launcher.design.widgets.GaugeView
import com.dynablox.launcher.design.widgets.LedIndicator
import com.dynablox.launcher.design.widgets.SkeuoPanel
import com.dynablox.launcher.design.widgets.Sparkline
import com.dynablox.launcher.di.AppContainer

/**
 * The floating performance monitor.
 *
 * Three real instrument layouts over one telemetry stream — mini strip, full instrument cluster and
 * graph mode with a live FPS trace. Every number comes from [PerfSnapshot]; nothing is simulated, and
 * when a source is unavailable the readout says so instead of inventing a value.
 */
class HudView(
    context: Context,
    private val container: AppContainer,
) : SkeuoPanel(SkeuoTheme.wrap(context, container.settings)), CommandFeedback {

    private val settings = container.settings
    private val themed = SkeuoTheme.wrap(context, settings)

    private val root = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        val pad = Dimens.dp(context, 10f).toInt()
        setPadding(pad, Dimens.dp(context, 8f).toInt(), pad, pad)
    }

    private val thermalLed = LedIndicator(themed).apply { labelText = "thermal" }

    private val fpsReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_fps)
        unit = "fps"
        digits = 3
    }
    private val frameReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_frame)
        unit = "ms"
    }
    private val dropReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_dropped)
    }
    private val cpuGauge = GaugeView(themed).apply {
        labelText = context.getString(R.string.hud_cpu)
        unit = "%"
        maxValue = 100f
        zones = listOf(
            GaugeView.Zone(0f, 60f, tokens().success),
            GaugeView.Zone(60f, 85f, tokens().warning),
            GaugeView.Zone(85f, 100f, tokens().danger),
        )
    }
    private val ramGauge = GaugeView(themed).apply {
        labelText = context.getString(R.string.hud_ram)
        unit = "%"
        maxValue = 100f
        zones = listOf(
            GaugeView.Zone(0f, 70f, tokens().success),
            GaugeView.Zone(70f, 90f, tokens().warning),
            GaugeView.Zone(90f, 100f, tokens().danger),
        )
    }
    private val batteryReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_battery)
        unit = "%"
    }
    private val tempReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_temp)
        unit = "°C"
    }
    private val netReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_net)
        unit = "kb/s"
    }
    private val pingReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_ping)
        unit = "ms"
    }
    private val sessionReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_session)
    }
    private val sourceLabel = TextView(themed).apply {
        textSize = 9f
        letterSpacing = 0.1f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(tokens().textFaint)
    }
    private val sparkline = Sparkline(themed).apply {
        capacity = 96
        maxValue = 120f
        accentOverride = tokens().accent
    }
    private val averageReadout = DigitalReadout(themed).apply {
        labelText = context.getString(R.string.hud_average)
        unit = "fps"
    }
    private val miniCpu = TextView(themed).apply { textSize = 11f; setTextColor(tokens().textSecondary) }
    private val miniRam = TextView(themed).apply { textSize = 11f; setTextColor(tokens().textSecondary) }

    private var mode: AppSettings.HudMode = settings.hudMode

    init {
        material = if (SkeuoTheme.translucent) Material.GLASS else Material.METAL
        cornerRadius = Dimens.dp(context, 18f)
        options = MaterialOptions(specular = false, shadow = 1f)
        addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        render(mode)
        contentDescription = context.getString(R.string.hud_a11y)
    }

    fun setMode(next: AppSettings.HudMode, rebuild: Boolean = true) {
        if (mode == next) return
        mode = next
        if (rebuild) render(next)
    }

    fun cycleMode(): AppSettings.HudMode {
        val values = AppSettings.HudMode.values()
        val next = values[(mode.ordinal + 1) % values.size]
        settings.hudMode = next
        setMode(next)
        return next
    }

    private fun render(mode: AppSettings.HudMode) {
        root.removeAllViews()
        val scale = settings.hudScale.coerceIn(0.7f, 1.6f)
        when (mode) {
            AppSettings.HudMode.MINI -> {
                val row = row(Gravity.CENTER_VERTICAL)
                row.addView(thermalLed, fixed(46f, 34f))
                row.addView(fpsReadout, fixed(66f, 34f))
                if (metricEnabled("cpu")) row.addView(miniCpu, textParams())
                if (metricEnabled("ram")) row.addView(miniRam, textParams())
                root.addView(row, wrap())
                root.addView(sourceLabel, wrap(top = 2f))
            }

            AppSettings.HudMode.FULL -> {
                val header = row(Gravity.CENTER_VERTICAL)
                header.addView(
                    TextView(themed).apply {
                        text = context.getString(R.string.hud_title)
                        textSize = 10f
                        letterSpacing = 0.16f
                        isAllCaps = true
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(tokens().textSecondary)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                header.addView(thermalLed, fixed(52f, 26f))
                root.addView(header, wrap())

                val first = row(Gravity.CENTER_VERTICAL)
                first.addView(fpsReadout, fixed(70f, 44f))
                if (metricEnabled("frame")) first.addView(frameReadout, fixed(64f, 44f))
                if (metricEnabled("drops")) first.addView(dropReadout, fixed(58f, 44f))
                root.addView(first, wrap(top = 6f))

                val second = row(Gravity.CENTER_VERTICAL)
                if (metricEnabled("cpu")) second.addView(cpuGauge, fixed(96f, 58f))
                if (metricEnabled("ram")) second.addView(ramGauge, fixed(96f, 58f))
                if (second.childCount > 0) root.addView(second, wrap(top = 4f))

                val third = row(Gravity.CENTER_VERTICAL)
                if (metricEnabled("batt")) third.addView(batteryReadout, fixed(58f, 40f))
                if (metricEnabled("temp")) third.addView(tempReadout, fixed(58f, 40f))
                if (third.childCount > 0) root.addView(third, wrap(top = 4f))

                val fourth = row(Gravity.CENTER_VERTICAL)
                if (metricEnabled("net")) fourth.addView(netReadout, fixed(72f, 40f))
                if (settings.hudPingEnabled) fourth.addView(pingReadout, fixed(62f, 40f))
                fourth.addView(sessionReadout, fixed(66f, 40f))
                root.addView(fourth, wrap(top = 4f))

                root.addView(sourceLabel, wrap(top = 4f))
            }

            AppSettings.HudMode.GRAPH -> {
                val header = row(Gravity.CENTER_VERTICAL)
                header.addView(fpsReadout, fixed(70f, 42f))
                header.addView(averageReadout, fixed(66f, 42f))
                header.addView(thermalLed, fixed(46f, 30f))
                root.addView(header, wrap())
                root.addView(
                    sparkline,
                    LinearLayout.LayoutParams(
                        Dimens.dp(context, (220f * scale)).toInt(),
                        Dimens.dp(context, (58f * scale)).toInt(),
                    ).apply { topMargin = Dimens.dp(context, 6f).toInt() },
                )
                val meters = row(Gravity.CENTER_VERTICAL)
                meters.addView(cpuGauge, fixed(96f, 52f))
                meters.addView(ramGauge, fixed(96f, 52f))
                root.addView(meters, wrap(top = 4f))
                root.addView(sourceLabel, wrap(top = 4f))
            }
        }
        requestLayout()
    }

    fun bind(snapshot: PerfSnapshot) {
        val metrics = settings.hudMetrics
        val thermalColor = when (snapshot.thermalSeverity) {
            0 -> tokens().success
            1 -> tokens().success
            2 -> tokens().warning
            else -> tokens().danger
        }
        thermalLed.lit = true
        thermalLed.colorOverride = thermalColor

        fpsReadout.value = if (snapshot.fps > 0f) Fmt.number(snapshot.fps, 0) else "--"
        fpsReadout.accentOverride = when {
            snapshot.fps <= 0f -> tokens().textFaint
            snapshot.headroom >= 0.9f -> tokens().success
            snapshot.headroom >= 0.6f -> tokens().warning
            else -> tokens().danger
        }
        frameReadout.value = if (snapshot.frameTimeMs > 0f) Fmt.number(snapshot.frameTimeMs, 1) else "--"
        dropReadout.value = snapshot.droppedFrames.toString()

        cpuGauge.value = snapshot.cpuPercent
        ramGauge.value = snapshot.ramPercent
        batteryReadout.value = if (snapshot.batteryPercent >= 0) snapshot.batteryPercent.toString() else "--"
        batteryReadout.accentOverride = when {
            snapshot.batteryCharging -> tokens().success
            snapshot.batteryPercent in 0..15 -> tokens().danger
            else -> null
        }
        tempReadout.value = if (snapshot.batteryTemperatureC > 0f) {
            Fmt.number(snapshot.batteryTemperatureC, 1)
        } else {
            "--"
        }
        netReadout.value = Fmt.number(snapshot.netDownKbps + snapshot.netUpKbps, 0)
        pingReadout.value = snapshot.pingMs?.let { Fmt.number(it, 0) } ?: "--"
        sessionReadout.value = Fmt.duration(snapshot.sessionSeconds)

        miniCpu.text = context.getString(R.string.hud_mini_cpu, Fmt.number(snapshot.cpuPercent, 0))
        miniRam.text = context.getString(
            R.string.hud_mini_ram,
            Fmt.number(snapshot.ramUsedMb, 0),
            Fmt.number(snapshot.ramTotalMb, 0),
        )

        sourceLabel.text = context.getString(
            R.string.hud_source,
            when (snapshot.fpsSource) {
                FpsSourceKind.CHOREOGRAPHER -> context.getString(R.string.hud_source_choreographer)
                FpsSourceKind.SURFACE_FLINGER -> snapshot.fpsLayer ?: "surfaceflinger"
                FpsSourceKind.NONE -> context.getString(R.string.hud_source_none)
            },
            snapshot.thermalLabel,
        )

        if (mode == AppSettings.HudMode.GRAPH) {
            sparkline.maxValue = (snapshot.refreshRate * 1.25f).coerceAtLeast(30f)
            sparkline.threshold = snapshot.refreshRate * 0.9f
            if (snapshot.fps > 0f) sparkline.push(snapshot.fps)
            averageReadout.value = Fmt.number(sparkline.average(), 0)
        }

        contentDescription = context.getString(
            R.string.hud_a11y_live,
            Fmt.number(snapshot.fps, 0),
            Fmt.number(snapshot.cpuPercent, 0),
            snapshot.thermalLabel,
        )
    }

    /** An empty selection means "everything on" — never hide the whole instrument cluster. */
    private fun metricEnabled(id: String): Boolean =
        settings.hudMetrics.isEmpty() || id in settings.hudMetrics

    fun resetTrace() = sparkline.reset()

    override fun show(message: String, kind: CommandFeedback.Kind) {
        container.toastFeedback.show(message, kind)
    }

    override fun refreshTheme() {
        super.refreshTheme()
        material = if (SkeuoTheme.translucent) Material.GLASS else Material.METAL
        cornerRadius = Dimens.dp(context, 18f)
        cpuGauge.zones = listOf(
            GaugeView.Zone(0f, 60f, tokens().success),
            GaugeView.Zone(60f, 85f, tokens().warning),
            GaugeView.Zone(85f, 100f, tokens().danger),
        )
        ramGauge.zones = listOf(
            GaugeView.Zone(0f, 70f, tokens().success),
            GaugeView.Zone(70f, 90f, tokens().warning),
            GaugeView.Zone(90f, 100f, tokens().danger),
        )
        sparkline.accentOverride = tokens().accent
        sourceLabel.setTextColor(tokens().textFaint)
        miniCpu.setTextColor(tokens().textSecondary)
        miniRam.setTextColor(tokens().textSecondary)
        listOf(fpsReadout, frameReadout, dropReadout, batteryReadout, tempReadout, netReadout, pingReadout,
            sessionReadout, averageReadout).forEach { it.refreshTheme() }
        listOf(thermalLed, cpuGauge, ramGauge, sparkline).forEach { it.refreshTheme() }
        render(mode)
    }

    private fun tokens() = SkeuoTheme.tokens(themed)

    private fun row(gravity: Int) = LinearLayout(themed).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    private fun fixed(widthDp: Float, heightDp: Float) = LinearLayout.LayoutParams(
        Dimens.dp(context, widthDp).toInt(),
        Dimens.dp(context, heightDp).toInt(),
    ).apply { marginEnd = Dimens.dp(context, 4f).toInt() }

    private fun textParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = Dimens.dp(context, 8f).toInt() }

    private fun wrap(top: Float = 0f) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = Dimens.dp(context, top).toInt() }

    companion object {
        /** Preferred window width per mode, in dp, before the interface scale is applied. */
        fun widthDp(mode: AppSettings.HudMode): Float = when (mode) {
            AppSettings.HudMode.MINI -> 210f
            AppSettings.HudMode.FULL -> 232f
            AppSettings.HudMode.GRAPH -> 244f
        }
    }
}

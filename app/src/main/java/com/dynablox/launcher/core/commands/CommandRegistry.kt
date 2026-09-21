package com.dynablox.launcher.core.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.MediaStore
import com.dynablox.launcher.R
import com.dynablox.launcher.controls.AccessibilityGlobalAction
import com.dynablox.launcher.accessibility.DynabloxAccessibilityService
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.core.roblox.RobloxLauncher
import com.dynablox.launcher.di.AppContainer
import com.dynablox.launcher.optimize.Optimizer
import com.dynablox.launcher.ui.ControlsActivity
import com.dynablox.launcher.ui.LauncherActivity
import com.dynablox.launcher.ui.PermissionsActivity
import com.dynablox.launcher.ui.SettingsActivity
import com.dynablox.launcher.ui.ShaderLabActivity

/**
 * The command catalogue.
 *
 * Built once per process; static commands come first, then dynamically discovered installed apps.
 * Search is a small scored matcher (prefix > keyword > substring) with accent folding, so the panel
 * filters instantly on every keystroke.
 */
class CommandRegistry(private val container: AppContainer) {

    private val context: Context get() = container.context

    private val staticCommands: List<Command> by lazy { buildStatic() }

    private val dynamicCommands: List<Command> by lazy { buildApps() }

    val all: List<Command>
        get() = staticCommands + dynamicCommands

    fun byId(id: String): Command? = all.firstOrNull { it.id == id }

    /** Slots for the radial arc, capped so the arc never crowds the screen. */
    fun radialCommands(settings: AppSettings, limit: Int = 8): List<Command> {
        val order = settings.hubOrder
        val pinned = order.mapNotNull { id -> staticCommands.firstOrNull { it.id == id && it.radial } }
        val rest = staticCommands.filter { it.radial && !pinned.contains(it) }
        return (pinned + rest).take(limit)
    }

    fun visibleCommands(settings: AppSettings): List<Command> {
        val hidden = settings.hubHiddenCategories
        val favorites = settings.hubFavorites
        val base = all.filter { it.category.id !in hidden }
        val ordered = applyOrder(base, settings.hubOrder)
        return ordered.sortedByDescending { it.id in favorites }
    }

    fun byCategory(category: CommandCategory, settings: AppSettings): List<Command> =
        applyOrder(all.filter { it.category == category }, settings.hubOrder)

    fun favorites(settings: AppSettings): List<Command> =
        applyOrder(all.filter { it.id in settings.hubFavorites }, settings.hubOrder)

    fun categories(settings: AppSettings): List<CommandCategory> =
        CommandCategory.values().toList().filter { it.id !in settings.hubHiddenCategories }

    private fun applyOrder(commands: List<Command>, order: List<String>): List<Command> {
        if (order.isEmpty()) return commands
        val rank = HashMap<String, Int>()
        order.forEachIndexed { index, id -> rank[id] = index }
        return commands.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    // ------------------------------------------------------------------ search

    data class SearchResult(val command: Command, val score: Int)

    fun search(query: String, settings: AppSettings): List<Command> {
        val normalized = fold(query.trim().lowercase())
        if (normalized.isEmpty()) return visibleCommands(settings)
        val hidden = settings.hubHiddenCategories
        val favorites = settings.hubFavorites
        val scored = ArrayList<SearchResult>()

        all.forEach { command ->
            if (command.category.id in hidden) return@forEach
            val title = fold(context.getString(command.titleRes).lowercase())
            val description = fold(context.getString(command.descriptionRes).lowercase())
            val category = fold(context.getString(command.category.labelRes).lowercase())
            val score = when {
                title == normalized -> 1000
                title.startsWith(normalized) -> 800
                command.keywords.any { fold(it.lowercase()).startsWith(normalized) } -> 700
                title.contains(normalized) -> 600
                category == normalized -> 520
                command.keywords.any { fold(it.lowercase()).contains(normalized) } -> 460
                category.startsWith(normalized) -> 420
                description.contains(normalized) -> 300
                else -> 0
            }
            if (score > 0) {
                val bonus = if (command.id in favorites) 120 else 0
                scored.add(SearchResult(command, score + bonus))
            }
        }
        return scored.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.command.id })
            .map { it.command }
    }

    private fun fold(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{Mn}+"), "")
    }

    // ------------------------------------------------------------------ catalogue

    private fun buildStatic(): List<Command> = listOf(
        Command(
            id = "roblox.launch",
            titleRes = R.string.cmd_launch_roblox,
            descriptionRes = R.string.cmd_launch_roblox_desc,
            iconRes = R.drawable.ic_blocks,
            category = CommandCategory.PROJECTS,
            keywords = listOf("roblox", "launch", "play", "jogar", "abrir", "client"),
            tint = CommandTint.ACCENT,
            radial = true,
        ) { ctx ->
            val settings = ctx.container.settings
            val placeId = settings.customPlaceId.ifBlank { settings.selectedPlaceId }
            when (ctx.container.roblox.launch(placeId)) {
                RobloxLauncher.LaunchOutcome.LAUNCHED ->
                    ctx.container.sessionStarted()

                RobloxLauncher.LaunchOutcome.DEEP_LINK_FAILED_FALLBACK -> {
                    ctx.container.sessionStarted()
                    ctx.feedback.show("Deep link unavailable — opened the Roblox client", CommandFeedback.Kind.WARNING)
                }

                RobloxLauncher.LaunchOutcome.NOT_INSTALLED ->
                    ctx.feedback.show(ctx.context.getString(R.string.err_no_roblox), CommandFeedback.Kind.ERROR)

                RobloxLauncher.LaunchOutcome.FAILED ->
                    ctx.feedback.show(ctx.context.getString(R.string.state_error), CommandFeedback.Kind.ERROR)
            }
        },

        Command(
            id = "roblox.experience",
            titleRes = R.string.launch_experience,
            descriptionRes = R.string.launch_experience,
            iconRes = R.drawable.ic_rocket,
            category = CommandCategory.PROJECTS,
            keywords = listOf("experience", "place", "world", "experiencia", "mapa"),
            tint = CommandTint.ACCENT,
            radial = true,
        ) { ctx ->
            ctx.container.launchExperiencePicker()
        },

        Command(
            id = "overlay.hud",
            titleRes = R.string.cmd_toggle_hud,
            descriptionRes = R.string.cmd_toggle_hud_desc,
            iconRes = R.drawable.ic_gauge,
            category = CommandCategory.PERFORMANCE,
            keywords = listOf("fps", "hud", "performance", "desempenho", "monitor", "overlay"),
            tint = CommandTint.SUCCESS,
            requirement = Requirement.OVERLAY,
            radial = true,
        ) { ctx ->
            ctx.container.toggleOverlaySafely(
                enabled = ctx.container.overlays.isHudRunning,
                start = { ctx.container.overlays.startHud() },
                stop = { ctx.container.overlays.stopHud() },
                startedMessage = ctx.context.getString(R.string.notif_hud_title),
                stoppedMessage = ctx.context.getString(R.string.state_stopped),
                feedback = ctx.feedback,
            )
        },

        Command(
            id = "overlay.hud.mode",
            titleRes = R.string.set_hud_mode,
            descriptionRes = R.string.hud_drag_hint,
            iconRes = R.drawable.ic_chart,
            category = CommandCategory.PERFORMANCE,
            keywords = listOf("mini", "full", "graph", "modo"),
            requirement = Requirement.OVERLAY,
        ) { ctx ->
            ctx.container.overlays.cycleHudMode()
        },

        Command(
            id = "system.optimize",
            titleRes = R.string.cmd_optimize,
            descriptionRes = R.string.cmd_optimize_desc,
            iconRes = R.drawable.ic_bolt,
            category = CommandCategory.PERFORMANCE,
            keywords = listOf("optimize", "boost", "otimizar", "ram", "limpar", "clean"),
            tint = CommandTint.WARNING,
            radial = true,
        ) { ctx ->
            val report = ctx.container.optimizer.run(ctx.container.settings.enabledOptimizations)
            val applied = report.entries.count { it.state == Optimizer.State.APPLIED }
            val failed = report.entries.count { it.state == Optimizer.State.FAILED }
            val freed = report.after.freeRamMb - report.before.freeRamMb
            val kind = when {
                failed > 0 -> CommandFeedback.Kind.WARNING
                applied > 0 -> CommandFeedback.Kind.SUCCESS
                else -> CommandFeedback.Kind.INFO
            }
            ctx.feedback.show(
                ctx.context.getString(
                    R.string.opt_result,
                    applied,
                    failed,
                    "${if (freed >= 0) "+" else ""}${"%.0f".format(freed)} MB",
                    "${report.durationMs} ms",
                ),
                kind,
            )
        },

        Command(
            id = "visual.shader",
            titleRes = R.string.cmd_shader_lab,
            descriptionRes = R.string.cmd_shader_lab_desc,
            iconRes = R.drawable.ic_shader,
            category = CommandCategory.VISUAL,
            keywords = listOf("shader", "glsl", "crt", "bloom", "filtro", "visual"),
            radial = true,
        ) { ctx ->
            ctx.context.startActivity(Intent(ctx.context, ShaderLabActivity::class.java).newTask())
        },

        Command(
            id = "visual.fx",
            titleRes = R.string.cmd_toggle_fx,
            descriptionRes = R.string.cmd_toggle_fx_desc,
            iconRes = R.drawable.ic_layers,
            category = CommandCategory.VISUAL,
            keywords = listOf("fx", "vignette", "grain", "scanline", "efeito", "tela"),
            requirement = Requirement.OVERLAY,
            radial = true,
        ) { ctx ->
            ctx.container.toggleOverlaySafely(
                enabled = ctx.container.overlays.isFxRunning,
                start = { ctx.container.overlays.startFx() },
                stop = { ctx.container.overlays.stopFx() },
                startedMessage = ctx.context.getString(R.string.notif_fx_title),
                stoppedMessage = ctx.context.getString(R.string.state_stopped),
                feedback = ctx.feedback,
            )
        },

        Command(
            id = "input.pad",
            titleRes = R.string.cmd_toggle_pad,
            descriptionRes = R.string.cmd_toggle_pad_desc,
            iconRes = R.drawable.ic_gamepad,
            category = CommandCategory.INPUT,
            keywords = listOf("gamepad", "controls", "controle", "virtual", "joystick", "shizuku"),
            tint = CommandTint.ACCENT,
            radial = true,
        ) { ctx ->
            if (!ctx.container.inputReady()) {
                ctx.feedback.show(ctx.context.getString(R.string.ctrl_requires), CommandFeedback.Kind.WARNING)
                ctx.context.startActivity(Intent(ctx.context, PermissionsActivity::class.java).newTask())
                return@Command
            }
            ctx.container.toggleOverlaySafely(
                enabled = ctx.container.overlays.isControlsRunning,
                start = { ctx.container.overlays.startControls() },
                stop = { ctx.container.overlays.stopControls() },
                startedMessage = ctx.context.getString(R.string.notif_pad_title),
                stoppedMessage = ctx.context.getString(R.string.state_stopped),
                feedback = ctx.feedback,
            )
        },

        Command(
            id = "input.preset",
            titleRes = R.string.ctrl_preset,
            descriptionRes = R.string.ctrl_subtitle,
            iconRes = R.drawable.ic_sliders,
            category = CommandCategory.INPUT,
            keywords = listOf("preset", "fling", "combat", "roleplay", "driving"),
        ) { ctx ->
            ctx.context.startActivity(Intent(ctx.context, ControlsActivity::class.java).newTask())
        },

        Command(
            id = "overlay.hub.hide",
            titleRes = R.string.cmd_toggle_hub,
            descriptionRes = R.string.cmd_toggle_hub_desc,
            iconRes = R.drawable.ic_eye,
            category = CommandCategory.SYSTEM,
            keywords = listOf("hide", "ocultar", "orb", "fechar", "hub"),
            destructive = true,
        ) { ctx ->
            ctx.container.overlays.stopMenuHub()
        },

        Command(
            id = "overlay.stopAll",
            titleRes = R.string.action_stop,
            descriptionRes = R.string.overlay_stop_all_desc,
            iconRes = R.drawable.ic_power,
            category = CommandCategory.SYSTEM,
            keywords = listOf("stop", "parar", "sair", "exit", "tudo"),
            tint = CommandTint.DANGER,
            destructive = true,
        ) { ctx ->
            ctx.container.overlays.stopEverything()
            ctx.feedback.show(ctx.context.getString(R.string.snack_stopped), CommandFeedback.Kind.SUCCESS)
        },

        Command(
            id = "system.theme",
            titleRes = R.string.cmd_theme,
            descriptionRes = R.string.cmd_theme_desc,
            iconRes = R.drawable.ic_palette,
            category = CommandCategory.VISUAL,
            keywords = listOf("theme", "dark", "light", "tema", "escuro", "claro"),
        ) { ctx ->
            ctx.container.cycleTheme()
            val mode = ctx.container.settings.themeMode
            ctx.feedback.show(
                ctx.context.getString(
                    R.string.snack_theme,
                    ctx.context.getString(
                        when (mode) {
                            AppSettings.ThemeMode.DARK -> R.string.set_theme_dark
                            AppSettings.ThemeMode.LIGHT -> R.string.set_theme_light
                            AppSettings.ThemeMode.SYSTEM -> R.string.set_theme_system
                        },
                    ),
                ),
                CommandFeedback.Kind.SUCCESS,
            )
        },

        Command(
            id = "app.settings",
            titleRes = R.string.cmd_settings,
            descriptionRes = R.string.cmd_settings_desc,
            iconRes = R.drawable.ic_settings,
            category = CommandCategory.SYSTEM,
            keywords = listOf("settings", "config", "ajustes", "preferencias"),
            radial = true,
        ) { ctx ->
            ctx.context.startActivity(Intent(ctx.context, SettingsActivity::class.java).newTask())
        },

        Command(
            id = "app.permissions",
            titleRes = R.string.cmd_permissions,
            descriptionRes = R.string.cmd_permissions_desc,
            iconRes = R.drawable.ic_shield,
            category = CommandCategory.SYSTEM,
            keywords = listOf("shizuku", "permission", "overlay", "a11y", "permissao"),
            tint = CommandTint.WARNING,
        ) { ctx ->
            ctx.context.startActivity(Intent(ctx.context, PermissionsActivity::class.java).newTask())
        },

        Command(
            id = "app.home",
            titleRes = R.string.cmd_go_home,
            descriptionRes = R.string.cmd_go_home_desc,
            iconRes = R.drawable.ic_home,
            category = CommandCategory.APPS,
            keywords = listOf("home", "inicio", "launcher", "inicio"),
        ) { ctx ->
            ctx.context.startActivity(Intent(ctx.context, LauncherActivity::class.java).newTask())
        },

        Command(
            id = "system.screenshot",
            titleRes = R.string.cmd_screenshot,
            descriptionRes = R.string.cmd_screenshot_desc,
            iconRes = R.drawable.ic_frame,
            category = CommandCategory.TOOLS,
            keywords = listOf("screenshot", "capture", "captura", "print", "screencap"),
            requirement = Requirement.SHIZUKU,
        ) { ctx ->
            val result = ctx.container.screencap()
            ctx.feedback.show(result, if (result.startsWith("/")) CommandFeedback.Kind.SUCCESS else CommandFeedback.Kind.WARNING)
        },

        Command(
            id = "system.battery",
            titleRes = R.string.cmd_battery,
            descriptionRes = R.string.cmd_battery_desc,
            iconRes = R.drawable.ic_battery,
            category = CommandCategory.SYSTEM,
            keywords = listOf("battery", "bateria", "doze", "exemption"),
            requirement = Requirement.BATTERY,
        ) { ctx ->
            ctx.container.requestBatteryExemption()
        },

        Command(
            id = "system.focus",
            titleRes = R.string.cmd_dnd,
            descriptionRes = R.string.cmd_dnd_desc,
            iconRes = R.drawable.ic_moon,
            category = CommandCategory.SYSTEM,
            keywords = listOf("dnd", "focus", "foco", "silenciar"),
            requirement = Requirement.NOTIFICATION_POLICY,
        ) { ctx ->
            ctx.feedback.show(ctx.container.toggleFocusMode(), CommandFeedback.Kind.INFO)
        },

        Command(
            id = "system.clearCache",
            titleRes = R.string.cmd_clear_cache,
            descriptionRes = R.string.cmd_clear_cache_desc,
            iconRes = R.drawable.ic_trash,
            category = CommandCategory.TOOLS,
            keywords = listOf("cache", "limpar", "memory", "memoria"),
        ) { ctx ->
            val freed = ctx.container.clearOwnCache()
            ctx.feedback.show("Cache freed: $freed KB", CommandFeedback.Kind.SUCCESS)
        },

        Command(
            id = "global.recents",
            titleRes = R.string.global_recents,
            descriptionRes = R.string.global_recents_desc,
            iconRes = R.drawable.ic_clock,
            category = CommandCategory.SYSTEM,
            keywords = listOf("recents", "recentes", "historico", "tasks"),
            requirement = Requirement.ACCESSIBILITY,
        ) { ctx ->
            ctx.container.globalAction(AccessibilityGlobalAction.RECENTS, ctx.feedback)
        },

        Command(
            id = "global.lock",
            titleRes = R.string.global_lock,
            descriptionRes = R.string.global_lock_desc,
            iconRes = R.drawable.ic_lock,
            category = CommandCategory.SYSTEM,
            keywords = listOf("lock", "bloquear", "screen off", "tela"),
            requirement = Requirement.ACCESSIBILITY,
        ) { ctx ->
            val service = DynabloxAccessibilityService.current
            if (service != null && service.lockScreen()) {
                ctx.feedback.show(ctx.context.getString(R.string.state_ready), CommandFeedback.Kind.SUCCESS)
            } else {
                ctx.feedback.show(ctx.context.getString(R.string.global_needs_a11y), CommandFeedback.Kind.WARNING)
            }
        },

        Command(
            id = "global.quickSettings",
            titleRes = R.string.global_quick_settings,
            descriptionRes = R.string.global_quick_settings_desc,
            iconRes = R.drawable.ic_sliders,
            category = CommandCategory.SYSTEM,
            keywords = listOf("quick settings", "painel", "atalhos"),
            requirement = Requirement.ACCESSIBILITY,
        ) { ctx ->
            ctx.container.globalAction(AccessibilityGlobalAction.QUICK_SETTINGS, ctx.feedback)
        },

        Command(
            id = "global.back",
            titleRes = R.string.action_back,
            descriptionRes = R.string.global_back_desc,
            iconRes = R.drawable.ic_chevron,
            category = CommandCategory.TOOLS,
            keywords = listOf("back", "voltar"),
            requirement = Requirement.ACCESSIBILITY,
        ) { ctx ->
            ctx.container.globalAction(AccessibilityGlobalAction.BACK, ctx.feedback)
        },

        Command(
            id = "media.music",
            titleRes = R.string.media_music,
            descriptionRes = R.string.media_music_desc,
            iconRes = R.drawable.ic_music,
            category = CommandCategory.MEDIA,
            keywords = listOf("music", "musica", "spotify", "player"),
        ) { ctx ->
            ctx.container.openSystemApp(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                ctx.feedback,
            )
        },

        Command(
            id = "comms.messages",
            titleRes = R.string.comms_messages,
            descriptionRes = R.string.comms_messages_desc,
            iconRes = R.drawable.ic_chat,
            category = CommandCategory.COMMUNICATION,
            keywords = listOf("messages", "sms", "mensagens", "whatsapp"),
        ) { ctx ->
            ctx.container.openSystemApp(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                ctx.feedback,
            )
        },

        Command(
            id = "productivity.calendar",
            titleRes = R.string.productivity_calendar,
            descriptionRes = R.string.productivity_calendar_desc,
            iconRes = R.drawable.ic_clock,
            category = CommandCategory.PRODUCTIVITY,
            keywords = listOf("calendar", "calendario", "agenda"),
        ) { ctx ->
            ctx.container.openSystemApp(
                Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI),
                ctx.feedback,
            )
        },

        Command(
            id = "files.browse",
            titleRes = R.string.files_browse,
            descriptionRes = R.string.files_browse_desc,
            iconRes = R.drawable.ic_folder,
            category = CommandCategory.FILES,
            keywords = listOf("files", "arquivos", "documents", "storage"),
        ) { ctx ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                    "vnd.android.document/root",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.container.openSystemApp(intent, ctx.feedback)
        },

        Command(
            id = "tools.camera",
            titleRes = R.string.tools_camera,
            descriptionRes = R.string.tools_camera_desc,
            iconRes = R.drawable.ic_eye,
            category = CommandCategory.TOOLS,
            keywords = listOf("camera", "foto", "photo"),
        ) { ctx ->
            ctx.container.openSystemApp(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ctx.feedback,
            )
        },

        Command(
            id = "tools.search",
            titleRes = R.string.menuhub_search_hint,
            descriptionRes = R.string.menuhub_search_hint,
            iconRes = R.drawable.ic_search,
            category = CommandCategory.TOOLS,
            keywords = listOf("search", "buscar", "busca", "find"),
        ) { ctx ->
            ctx.container.overlays.toggleMenuHubPanel()
        },

        Command(
            id = "tools.webSearch",
            titleRes = R.string.tools_web_search,
            descriptionRes = R.string.tools_web_search_desc,
            iconRes = R.drawable.ic_link,
            category = CommandCategory.TOOLS,
            keywords = listOf("web", "browser", "google", "navegador"),
        ) { ctx ->
            ctx.container.openSystemApp(Intent(Intent.ACTION_WEB_SEARCH).newTask(), ctx.feedback)
        },
    )

    private fun buildApps(): List<Command> {
        val apps = try {
            container.roblox.launchableApps(limit = 20)
        } catch (_: Throwable) {
            emptyList()
        }
        return apps.map { app ->
            Command(
                id = "app:${app.packageName}",
                titleRes = R.string.app_entry_title,
                descriptionRes = R.string.app_entry_desc,
                iconRes = R.drawable.ic_grid,
                category = CommandCategory.APPS,
                keywords = listOf(app.label.lowercase(), app.packageName),
                pinnable = false,
            ) { ctx ->
                if (!ctx.container.roblox.launchPackage(app.packageName)) {
                    ctx.feedback.show(app.label, CommandFeedback.Kind.ERROR)
                }
            }
        }
    }

    private fun Intent.newTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

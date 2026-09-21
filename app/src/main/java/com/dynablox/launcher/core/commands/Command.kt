package com.dynablox.launcher.core.commands

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Command buckets. Each one has its own glyph and a subtle tint — never a rainbow. */
enum class CommandCategory(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    PERFORMANCE("performance", com.dynablox.launcher.R.string.cat_performance, com.dynablox.launcher.R.drawable.ic_gauge),
    VISUAL("visual", com.dynablox.launcher.R.string.cat_visual, com.dynablox.launcher.R.drawable.ic_palette),
    INPUT("input", com.dynablox.launcher.R.string.cat_input, com.dynablox.launcher.R.drawable.ic_gamepad),
    PROJECTS("projects", com.dynablox.launcher.R.string.cat_projects, com.dynablox.launcher.R.drawable.ic_blocks),
    APPS("apps", com.dynablox.launcher.R.string.cat_apps, com.dynablox.launcher.R.drawable.ic_grid),
    SYSTEM("system", com.dynablox.launcher.R.string.cat_system, com.dynablox.launcher.R.drawable.ic_cpu),
    TOOLS("tools", com.dynablox.launcher.R.string.cat_tools, com.dynablox.launcher.R.drawable.ic_wrench),
    MEDIA("media", com.dynablox.launcher.R.string.cat_media, com.dynablox.launcher.R.drawable.ic_music),
    COMMUNICATION("communication", com.dynablox.launcher.R.string.cat_comms, com.dynablox.launcher.R.drawable.ic_chat),
    FILES("files", com.dynablox.launcher.R.string.cat_files, com.dynablox.launcher.R.drawable.ic_folder),
    PRODUCTIVITY("productivity", com.dynablox.launcher.R.string.cat_productivity, com.dynablox.launcher.R.drawable.ic_check),
    FAVORITES("favorites", com.dynablox.launcher.R.string.cat_favorites, com.dynablox.launcher.R.drawable.ic_star),
    RECENTS("recents", com.dynablox.launcher.R.string.cat_recents, com.dynablox.launcher.R.drawable.ic_clock),
}

/** Restrained accent used by a command's glyph — resolved against the active theme tokens. */
enum class CommandTint { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

/** What a command needs before it can actually do its job. */
enum class Requirement { NONE, OVERLAY, SHIZUKU, ACCESSIBILITY, NOTIFICATION_POLICY, BATTERY }

/** Sink for command feedback (inline status pill in the hub, Snackbar in the launcher). */
interface CommandFeedback {
    enum class Kind { INFO, SUCCESS, WARNING, ERROR }

    fun show(message: String, kind: Kind = Kind.INFO)
}

/** Everything a command may touch while executing. */
class CommandContext(
    val context: Context,
    val container: com.dynablox.launcher.di.AppContainer,
    val feedback: CommandFeedback,
)

/**
 * One executable entry in the MenuHub.
 *
 * Commands are data, not screens: the radial menu, the panel grid, the launcher deck, the
 * notification actions and the search index are all rendered from the same list, so a shortcut can
 * be reordered, pinned or hidden without touching the UI code.
 */
class Command(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val category: CommandCategory,
    val keywords: List<String> = emptyList(),
    val tint: CommandTint = CommandTint.NEUTRAL,
    val destructive: Boolean = false,
    val requirement: Requirement = Requirement.NONE,
    /** Rendered in the radial arc around the orb (max 8 slots). */
    val radial: Boolean = false,
    /** False when the command is a dynamic entry (installed apps) and must not be persisted. */
    val pinnable: Boolean = true,
    val action: suspend (CommandContext) -> Unit = {},
) {
    fun title(context: Context): String = context.getString(titleRes)
    fun description(context: Context): String = context.getString(descriptionRes)
}

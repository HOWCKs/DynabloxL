package com.dynablox.launcher.core.roblox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import com.dynablox.launcher.R

/** A curated Roblox experience the launcher can deep-link straight into. */
data class Experience(
    val name: String,
    val placeId: String,
    val genre: String,
    @DrawableRes val iconRes: Int,
)

enum class LaunchOutcome { LAUNCHED, DEEP_LINK_FAILED_FALLBACK, NOT_INSTALLED, FAILED }

/**
 * Everything Roblox-related: detection, client launch, experience deep links and the Play Store
 * fallback. Place IDs are plain `roblox://experiences/start?placeId=` deep links — the same scheme
 * the official client registers.
 */
class RobloxLauncher(private val context: Context) {

    fun isInstalled(): Boolean = try {
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null
    } catch (_: Throwable) {
        false
    }

    fun versionName(): String? = try {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    } catch (_: Throwable) {
        null
    }

    /** Launches the client, optionally jumping straight into an experience. */
    fun launch(placeId: String? = null): LaunchOutcome {
        if (!isInstalled()) return LaunchOutcome.NOT_INSTALLED

        if (!placeId.isNullOrBlank()) {
            val uri = Uri.parse("roblox://experiences/start?placeId=${placeId.trim()}")
            val deepLink = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(PACKAGE)
            }
            try {
                context.startActivity(deepLink)
                return LaunchOutcome.LAUNCHED
            } catch (_: ActivityNotFoundException) {
                // Fall through to the plain client launch.
            } catch (_: SecurityException) {
                // Fall through to the plain client launch.
            }
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: return LaunchOutcome.FAILED
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (placeId.isNullOrBlank()) LaunchOutcome.LAUNCHED
            else LaunchOutcome.DEEP_LINK_FAILED_FALLBACK
        } catch (_: Throwable) {
            LaunchOutcome.FAILED
        }
    }

    fun openPlayStore(): Boolean = try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Resolves the launcher intent for any installed app (used by the app category). */
    fun launchPackage(pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun launchableApps(limit: Int = 60): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .take(limit)
                .map { AppEntry(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    companion object {
        const val PACKAGE = "com.roblox.client"

        /** Curated library — users can add any place ID from the launcher. */
        val EXPERIENCES: List<Experience> = listOf(
            Experience("Brookhaven RP", "4924922222", "Roleplay", R.drawable.ic_home),
            Experience("Blox Fruits", "2753915549", "Adventure", R.drawable.ic_blocks),
            Experience("Adopt Me!", "920587237", "Family", R.drawable.ic_star),
            Experience("DOORS", "6516141723", "Horror", R.drawable.ic_lock),
            Experience("Tower of Hell", "1962086868", "Obby", R.drawable.ic_layers),
            Experience("Jailbreak", "606849621", "Action", R.drawable.ic_shield),
            Experience("Murder Mystery 2", "142823291", "Mystery", R.drawable.ic_eye),
            Experience("Arsenal", "2788229376", "Shooter", R.drawable.ic_target),
            Experience("Bee Swarm Simulator", "1534453621", "Simulator", R.drawable.ic_palette),
        )
    }
}

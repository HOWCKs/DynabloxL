package com.dynablox.launcher.core.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dynablox.launcher.R

/**
 * Notification plumbing for the four overlay services.
 *
 * Channels are grouped by what the user cares about ("something is floating on my screen" vs
 * "telemetry is running"), and every notification carries a Stop action so an overlay can never
 * become stuck.
 */
class Notifications(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val overlay = NotificationChannel(
            CHANNEL_OVERLAY,
            context.getString(R.string.notif_channel_overlay),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_overlay_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val telemetry = NotificationChannel(
            CHANNEL_TELEMETRY,
            context.getString(R.string.notif_channel_telemetry),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notif_channel_telemetry_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(overlay, telemetry))
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun build(
        channelId: String,
        title: String,
        text: String,
        contentIntent: PendingIntent?,
        actions: List<Pair<String, PendingIntent>> = emptyList(),
        ongoing: Boolean = true,
    ): Notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_hub)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(contentIntent)
        .setPriority(
            if (channelId == CHANNEL_TELEMETRY) NotificationCompat.PRIORITY_MIN
            else NotificationCompat.PRIORITY_LOW,
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(ongoing)
        .setShowWhen(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .apply {
            actions.forEach { (label, intent) ->
                addAction(R.drawable.ic_stop, label, intent)
            }
        }
        .build()

    fun pendingActivity(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun pendingService(service: Class<*>, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context, requestCode,
            Intent(context, service).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Posts (or refreshes) a notification, ignoring the case where the user revoked them. */
    fun notify(id: Int, notification: Notification) {
        if (!areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: Throwable) {
            // Permission revoked mid-session; the overlay keeps working without its notification.
        }
    }

    fun cancel(id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: Throwable) {
            // Ignore.
        }
    }

    fun pendingBroadcast(service: Class<*>, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, service).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_OVERLAY = "dbx_overlay"
        const val CHANNEL_TELEMETRY = "dbx_telemetry"

        const val ID_MENU_HUB = 1001
        const val ID_PERF_HUD = 1002
        const val ID_CONTROLS = 1003
        const val ID_SCREEN_FX = 1004
    }
}

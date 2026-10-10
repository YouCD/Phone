package org.fossify.phone.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity

const val BOOT_NOTIFICATION_CHANNEL_ID = "boot_notification"
const val BOOT_NOTIFICATION_ID = 1001

// After a reboot some ROMs reset the default dialer role (or clear the in-call service
// selection), which prevents Telecom from binding our InCallService and thus no
// incoming call UI is ever shown. We can't reclaim the role programmatically,
// so detect it on boot and notify the user to re-set it.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && !context.isDefaultDialer()) {
            notifyDefaultDialerMissing(context)
        }
    }

    private fun notifyDefaultDialerMissing(context: Context) {
        if (!context.hasPermission(PERMISSION_POST_NOTIFICATIONS)) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channelId = BOOT_NOTIFICATION_CHANNEL_ID
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.boot_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(context.getString(R.string.app_launcher_name))
            .setContentText(context.getString(R.string.default_dialer_not_set_after_boot))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
    }
}

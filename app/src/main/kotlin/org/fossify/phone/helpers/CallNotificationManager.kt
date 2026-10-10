package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.telecom.Call
import android.widget.RemoteViews
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setText
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.commons.helpers.isQPlus
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.models.CallContact
import org.fossify.phone.receivers.CallActionReceiver
import java.util.concurrent.atomic.AtomicLong

class CallNotificationManager(private val context: Context) {
    companion object {
        private const val CALL_NOTIFICATION_ID = 42
        private const val ACCEPT_CALL_CODE = 0
        private const val DECLINE_CALL_CODE = 1
        private const val DEFAULT_CHANNEL_ID = "simple_dialer_call"
        private const val HIGH_PRIORITY_CHANNEL_ID = "simple_dialer_call_high_priority"
    }

    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    // bumped on every setup/cancel, so late callbacks of obsolete requests are dropped
    private val notificationGeneration = AtomicLong()

    @SuppressLint("NewApi")
    fun setupNotification(lowPriority: Boolean = false) {
        val generation = notificationGeneration.incrementAndGet()
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            // resolving the contact is async, a newer state change (or a cancel) may have happened meanwhile
            if (generation != notificationGeneration.get()) {
                return@getCallContact
            }

            val callState = CallManager.getState() ?: Call.STATE_DISCONNECTED
            if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
                cancelNotification()
                return@getCallContact
            }

            val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
            val channelId = if (isHighPriority) HIGH_PRIORITY_CHANNEL_ID else DEFAULT_CHANNEL_ID
            createNotificationChannel(isHighPriority, channelId)

            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                CallActivity.getStartIntent(context),
                PendingIntent.FLAG_MUTABLE
            )

            val callerName = getCallerName(callContact)
            val statusText = context.getString(contentTextId(callState))
            val avatar = callContactAvatarHelper.getCallContactAvatar(callContact)

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                // the collapsed view uses the system template, so the elapsed call time is visible
                .setContentTitle(callerName)
                .setContentText(statusText)
                .setCustomBigContentView(getExpandedView(callState, callerName, statusText, avatar))
                .setOngoing(callState == Call.STATE_RINGING)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(Notification.DecoratedCustomViewStyle())

            if (avatar != null) {
                builder.setLargeIcon(callContactAvatarHelper.getCircularBitmap(avatar))
            }

            // setFullScreenIntent is API 29, while the minSdk is 26
            if (isHighPriority && isQPlus()) {
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            if (CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    fun createNotificationChannel(isHighPriority: Boolean, channelId: String) {
        val name = if (isHighPriority) {
            context.getString(R.string.call_notification_channel_high_priority)
        } else {
            context.getString(R.string.call_notification_channel)
        }

        val importance = if (isHighPriority) IMPORTANCE_HIGH else IMPORTANCE_DEFAULT
        NotificationChannel(channelId, name, importance).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }

    fun cancelNotification() {
        notificationGeneration.incrementAndGet()
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    private fun getExpandedView(callState: Int, callerName: String, statusText: String, avatar: Bitmap?): RemoteViews {
        return RemoteViews(context.packageName, R.layout.call_notification).apply {
            setText(R.id.notification_caller_name, callerName)
            setText(R.id.notification_call_status, statusText)
            setVisibleIf(R.id.notification_accept_call, callState == Call.STATE_RINGING)

            val declinePendingIntent = getCallActionPendingIntent(DECLINE_CALL, DECLINE_CALL_CODE)
            val acceptPendingIntent = getCallActionPendingIntent(ACCEPT_CALL, ACCEPT_CALL_CODE)
            setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
            setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

            if (avatar != null) {
                setImageViewBitmap(R.id.notification_thumbnail, callContactAvatarHelper.getCircularBitmap(avatar))
            }
        }
    }

    private fun getCallActionPendingIntent(action: String, requestCode: Int) = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, CallActionReceiver::class.java).apply { this.action = action },
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    private fun getCallerName(callContact: CallContact): String {
        val name = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
        return if (callContact.numberLabel.isNotEmpty()) {
            "$name - ${callContact.numberLabel}"
        } else {
            name
        }
    }

    private fun contentTextId(callState: Int): Int = when (callState) {
        Call.STATE_RINGING -> R.string.is_calling
        Call.STATE_DIALING -> R.string.dialing
        Call.STATE_DISCONNECTED -> R.string.call_ended
        Call.STATE_DISCONNECTING -> R.string.call_ending
        else -> R.string.ongoing_call
    }
}

package org.fossify.phone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.text.format.DateFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimpleCallScreeningService : CallScreeningService() {

    companion object {
        private const val INTERVAL_MS = 3 * 60 * 1000L
        private val callHistory = HashMap<String, Long>()
        private const val INTERCEPT_NOTIFICATION_ID = 100
        private const val INTERCEPT_CHANNEL_ID = "call_interception"
        private val phoneNumberUtil = PhoneNumberUtil.getInstance()
        private val geocoder = PhoneNumberOfflineGeocoder.getInstance()

        /**
         * Unknown-number interception rules (only applied when call interception is on):
         * - first incoming call from the number (or the previous one is older than [INTERVAL_MS]) -> block it
         * - a second incoming call within [INTERVAL_MS] of the blocked first one -> let it through
         */
        private fun shouldBlockUnknownCall(number: String): Boolean {
            val now = System.currentTimeMillis()
            val firstCallTime = callHistory[number]
            return if (firstCallTime == null || now - firstCallTime > INTERVAL_MS) {
                callHistory[number] = now
                true // first call -> block it
            } else {
                callHistory.remove(number)
                false // second call within the window -> let it through
            }
        }

        private fun cleanupHistory() {
            val cutoff = System.currentTimeMillis() - INTERVAL_MS
            callHistory.entries.removeAll { it.value < cutoff }
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Call interception only applies to incoming calls. Telecom also invokes the screening
        // service for outgoing calls; without this guard every outgoing call to a number that
        // is not in the contacts would post a "call intercepted" notification.
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondToCall(callDetails, isBlocked = false)
            return
        }

        val number = callDetails.handle?.schemeSpecificPart

        cleanupHistory()

        when {
            number != null && isNumberBlocked(number) -> {
                respondToCall(callDetails, isBlocked = true)
            }

            number != null && config.callInterceptionEnabled -> {
                handleInterception(number, callDetails)
            }

            number != null && baseConfig.blockUnknownNumbers -> {
                val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                val result = SimpleContactsHelper(this).existsSync(number, privateCursor)
                respondToCall(callDetails, isBlocked = result == ContactLookupResult.NotFound)
            }

            number == null && baseConfig.blockHiddenNumbers -> {
                respondToCall(callDetails, isBlocked = true)
            }

            else -> {
                respondToCall(callDetails, isBlocked = false)
            }
        }
    }

    private fun handleInterception(number: String, callDetails: Call.Details) {
        val allContactsCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        val isInContacts = SimpleContactsHelper(this).existsSync(number, allContactsCursor)

        if (isInContacts == ContactLookupResult.Found) {
            // contacts are always let through
            respondToCall(callDetails, isBlocked = false)
            return
        }

        if (shouldBlockUnknownCall(number)) {
            showInterceptNotification(number)
            respondToCall(callDetails, isBlocked = true)
        } else {
            respondToCall(callDetails, isBlocked = false)
        }
    }

    private fun showInterceptNotification(number: String) {
        if (!hasPermission(PERMISSION_POST_NOTIFICATIONS)) {
            return
        }

        createInterceptChannel()
        val timePattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date())
        val location = try {
            val phoneNumber = phoneNumberUtil.parse(number, Locale.getDefault().country)
            geocoder.getDescriptionForNumber(phoneNumber, Locale.getDefault())
        } catch (_: Exception) {
            null
        }
        val details = listOf(timeStr, number, location).filterNotNull().joinToString(" - ")
        val text = getString(R.string.call_intercepted_desc, details)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val builder = Notification.Builder(this, INTERCEPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(getString(R.string.call_intercepted))
            .setContentText(text)
            .setAutoCancel(true)

        if (launchIntent != null) {
            val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)
        }

        getSystemService(NotificationManager::class.java).notify(INTERCEPT_NOTIFICATION_ID, builder.build())
    }

    private fun createInterceptChannel() {
        val channel = NotificationChannel(
            INTERCEPT_CHANNEL_ID,
            getString(R.string.call_intercepted),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}

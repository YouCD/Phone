package org.fossify.phone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallScreeningService
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.ContactLookupResult
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

        private fun checkRepetition(number: String): Boolean {
            val now = System.currentTimeMillis()
            val lastTime = callHistory[number]
            return if (lastTime != null && (now - lastTime) <= INTERVAL_MS) {
                callHistory.remove(number)
                true
            } else {
                callHistory[number] = now
                false
            }
        }

        private fun cleanupHistory() {
            val cutoff = System.currentTimeMillis() - INTERVAL_MS
            callHistory.entries.removeAll { it.value < cutoff }
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
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
            if (isPhoneSilent()) {
                val favCursor = getMyContactsCursor(favoritesOnly = true, withPhoneNumbersOnly = true)
                val isFavorite = SimpleContactsHelper(this).existsSync(number, favCursor)
                if (isFavorite == ContactLookupResult.Found) {
                    respondToCall(callDetails, isBlocked = false)
                } else {
                    val allowed = checkRepetition(number)
                    if (allowed) {
                        respondToCall(callDetails, isBlocked = false)
                    } else {
                        showInterceptNotification(number)
                        respondToCall(callDetails, isBlocked = true)
                    }
                }
            } else {
                respondToCall(callDetails, isBlocked = false)
            }
        } else {
            val allowed = checkRepetition(number)
            if (allowed) {
                respondToCall(callDetails, isBlocked = false)
            } else {
                showInterceptNotification(number)
                respondToCall(callDetails, isBlocked = true)
            }
        }
    }

    private fun showInterceptNotification(number: String) {
        createInterceptChannel()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val location = try {
            val phoneNumber = phoneNumberUtil.parse(number, Locale.getDefault().country)
            geocoder.getDescriptionForNumber(phoneNumber, Locale.getDefault())
        } catch (_: Exception) {
            null
        }
        val text = if (location != null) "$timeStr - $number - $location" else "$timeStr - $number"
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, INTERCEPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(getString(R.string.call_intercepted))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(INTERCEPT_NOTIFICATION_ID, notification)
    }

    private fun createInterceptChannel() {
        val channel = NotificationChannel(
            INTERCEPT_CHANNEL_ID,
            getString(R.string.call_intercepted),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun isPhoneSilent(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
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

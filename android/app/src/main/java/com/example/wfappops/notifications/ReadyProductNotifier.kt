package com.example.wfappops.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibratorManager
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.wfappops.MainActivity
import com.example.wfappops.R
import com.example.wfappops.orders.model.ReadyProductNotice

internal data class ReadyNotificationRecipient(val storageId: String, val employeeId: String, val name: String) {
    val tag get() = "${Uri.encode(storageId)}/${Uri.encode(employeeId)}"
}

internal class ReadyProductNotifier(context: Context) {
    private val app = context.applicationContext
    private val manager = checkNotNull(app.getSystemService(NotificationManager::class.java))
    private val preferences = app.getSharedPreferences("ready-product-notifications", Context.MODE_PRIVATE)

    fun enabled(): Boolean = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun requirementsIssue(): NotificationIssue? {
        ensureChannel()
        val channel = manager.getNotificationChannel(CHANNEL)
        val audio = checkNotNull(app.getSystemService(AudioManager::class.java))
        val canVibrate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
        } else {
            app.getSystemService(Vibrator::class.java)?.hasVibrator() == true
        }
        val sound = channel?.sound
        val soundConfigured = sound != null && (!RingtoneManager.isDefault(sound) ||
            RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_NOTIFICATION) != null)
        return NotificationReadiness(
            allowed = enabled(),
            channelSound = soundConfigured && (channel?.importance ?: 0) >= NotificationManager.IMPORTANCE_DEFAULT,
            hasVibrator = canVibrate,
            channelVibration = channel?.shouldVibrate() == true,
            deviceAudible = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) > 0,
            doNotDisturb = manager.currentInterruptionFilter !in listOf(NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        ).issue
    }

    fun openRequiredSettings(issue: NotificationIssue) {
        if (issue == NotificationIssue.PERMISSION || issue == NotificationIssue.CHANNEL) {
            openSoundSettings()
            return
        }
        try {
            app.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openSoundSettings() {
        ensureChannel()
        val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val channelSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            app.startActivity(if (manager.areNotificationsEnabled()) channelSettings else appSettings)
        } catch (_: ActivityNotFoundException) {
            app.startActivity(appSettings)
        }
    }

    fun shouldRequestPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            preferences.getBoolean(PERMISSION_REQUESTED, false)
        ) return false

        preferences.edit().putBoolean(PERMISSION_REQUESTED, true).apply()
        return true
    }

    fun selectRecipient(recipient: ReadyNotificationRecipient?) {
        manager.activeNotifications.forEach { active ->
            if (active.notification.channelId == CHANNEL && active.tag != recipient?.tag) {
                manager.cancel(active.tag, active.id)
            }
        }
    }

    fun sync(recipient: ReadyNotificationRecipient, notices: List<ReadyProductNotice>) {
        if (!enabled()) return
        val tag = recipient.tag
        val current = notices.mapTo(mutableSetOf()) { notice ->
            val key = notice.key
            "${Uri.encode(key.visitId)}/${key.dispatchIndex}/${key.lineIndex}"
        }
        val previous = preferences.getStringSet(tag, emptySet()).orEmpty()
        if (current == previous) return

        val hasNewMessages = current.any { it !in previous }
        val active = manager.activeNotifications.any { it.id == NOTIFICATION_ID && it.tag == tag }
        if (current.isEmpty()) {
            manager.cancel(tag, NOTIFICATION_ID)
        } else if (active || hasNewMessages) {
            ensureChannel()
            try {
                manager.notify(tag, NOTIFICATION_ID, buildNotification(recipient, notices, silent = !hasNewMessages))
            } catch (_: SecurityException) {
                return
            }
        }
        preferences.edit().putStringSet(tag, current).apply()
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, app.getString(R.string.ready_notification_channel), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = app.getString(R.string.ready_notification_channel_description)
            setSound(defaultSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            enableVibration(true)
        })
    }

    private fun buildNotification(
        recipient: ReadyNotificationRecipient,
        notices: List<ReadyProductNotice>,
        silent: Boolean
    ): Notification {
        val kitchen = Person.Builder().setName(app.getString(R.string.ops_kitchen)).setKey(CHANNEL).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(recipient.name).build())
            .setGroupConversation(false)
        notices.takeLast(NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES).forEach { notice ->
            style.addMessage(message(notice), notice.readyAt.toEpochMilli(), kitchen)
        }
        val launch = Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openApp = PendingIntent.getActivity(app, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val last = notices.last()
        return NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_kitchen)
            .setContentTitle(app.getString(R.string.ops_kitchen))
            .setContentText(message(last))
            .setContentIntent(openApp)
            .setWhen(last.readyAt.toEpochMilli())
            .setNumber(notices.size)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setSilent(silent)
            .setStyle(style)
            .build()
    }

    private fun message(notice: ReadyProductNotice): String {
        val table = notice.tableLabel ?: app.getString(R.string.order_table_number, notice.table)
        val product = if (notice.quantity == 1) notice.product else {
            app.getString(R.string.kitchen_quantity, notice.quantity, notice.product)
        }
        return app.resources.getQuantityString(R.plurals.ready_notification_message, notice.quantity, table, product)
    }

    private companion object {
        const val CHANNEL = "kitchen-ready"
        const val NOTIFICATION_ID = 2101
        const val PERMISSION_REQUESTED = "permission-requested"
        val defaultSound: Uri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}

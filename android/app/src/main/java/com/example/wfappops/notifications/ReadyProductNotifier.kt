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
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.orders.model.ReadyProductNotice
import java.time.Instant

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

    fun selectRecipient(recipient: ReadyNotificationRecipient?) = synchronized(syncLock) {
        preferences.edit().putString(ACTIVE_RECIPIENT, recipient?.tag).apply()
        manager.activeNotifications.forEach { active ->
            if (active.notification.channelId == CHANNEL && active.tag != recipient?.tag) {
                manager.cancel(active.tag, active.id)
            }
        }
    }

    fun sync(recipient: ReadyNotificationRecipient, notices: List<ReadyProductNotice>, now: Instant = Instant.now()) = synchronized(syncLock) {
        if (!app.hasOpsAccess(recipient.storageId) || preferences.getString(ACTIVE_RECIPIENT, recipient.tag) != recipient.tag) return@synchronized
        val update = NotificationHistoryStore(app, recipient).sync(notices, now)
        // El historial se conserva incluso si Android no permite sonido o publicación de avisos.
        if (!enabled()) return@synchronized
        val tag = recipient.tag
        val current = notices.mapTo(mutableSetOf()) { it.identity }
        val previous = preferences.getStringSet(tag, emptySet()).orEmpty()
        val postedKeys = preferences.getStringSet("posted/$tag", emptySet()).orEmpty()
        val hasNewMessages = current.any { it !in postedKeys }
        if (current == previous && update.reminders.isEmpty() && update.arrivals.isEmpty() && !hasNewMessages) return@synchronized
        val remind = update.reminders.isNotEmpty() && !hasNewMessages
        val active = manager.activeNotifications.any { it.id == NOTIFICATION_ID && it.tag == tag }
        if (current.isEmpty()) {
            manager.cancel(tag, NOTIFICATION_ID)
        } else if (active || hasNewMessages || remind) {
            ensureChannel()
            try {
                val entries = if (remind) update.reminders else notices.groupBy { it.groupIdentity }.map { (key, group) ->
                    val first = group.first()
                    LocalNotification(key, LocalNotificationKind.READY, first.product, group.sumOf { it.quantity },
                        first.tableLabel ?: first.table.toString(), first.orderNumber, first.locationId,
                        group.mapNotNull { update.history.firstSeen[it.identity] }.maxOrNull() ?: now, first.locationName)
                }
                manager.notify(tag, NOTIFICATION_ID, buildNotification(recipient, entries, silent = !hasNewMessages && !remind))
                preferences.edit().putStringSet("posted/$tag", postedKeys + current).apply()
            } catch (_: SecurityException) {
                return@synchronized
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
        notices: List<LocalNotification>,
        silent: Boolean
    ): Notification {
        val kitchen = Person.Builder().setName(app.getString(R.string.ready_notification_channel)).setKey(CHANNEL).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(recipient.name).build())
            .setGroupConversation(false)
        notices.takeLast(NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES).forEach { notice ->
            style.addMessage(message(notice), notice.receivedAt.toEpochMilli(), kitchen)
        }
        val launch = Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openApp = PendingIntent.getActivity(app, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val last = notices.last()
        return NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_kitchen)
            .setContentTitle(app.getString(if (last.kind == LocalNotificationKind.SERVE_REMINDER)
                R.string.notification_serve_reminder_title else R.string.notification_ready_title))
            .setContentText(message(last))
            .setContentIntent(openApp)
            .setWhen(last.receivedAt.toEpochMilli())
            .setNumber(notices.size)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setSilent(silent)
            .setStyle(style)
            .build()
    }

    private fun message(notice: LocalNotification): String {
        val product = if (notice.quantity == 1) notice.product else "${notice.quantity} × ${notice.product}"
        val order = notice.orderNumber?.let { app.getString(R.string.order_number, it) }
        val detail = app.getString(if (notice.kind == LocalNotificationKind.SERVE_REMINDER)
            R.string.notification_serve_reminder_body else R.string.notification_ready_body, product)
        return listOfNotNull(detail, order, app.getString(R.string.notification_table_label, notice.tableLabel)).joinToString(" · ")
    }

    private companion object {
        const val CHANNEL = "kitchen-ready"
        const val NOTIFICATION_ID = 2101
        const val PERMISSION_REQUESTED = "permission-requested"
        const val ACTIVE_RECIPIENT = "active-recipient"
        val syncLock = Any()
        val defaultSound: Uri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}

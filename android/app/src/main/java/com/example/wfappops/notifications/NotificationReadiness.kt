package com.example.wfappops.notifications

internal enum class NotificationIssue { PERMISSION, CHANNEL, DEVICE_SOUND, DO_NOT_DISTURB }

/** Los equipos sin motor de vibración solo requieren las capacidades que realmente tienen. */
internal data class NotificationReadiness(
    val allowed: Boolean,
    val channelSound: Boolean,
    val hasVibrator: Boolean,
    val channelVibration: Boolean,
    val deviceAudible: Boolean,
    val doNotDisturb: Boolean
) {
    val issue: NotificationIssue? get() = when {
        !allowed -> NotificationIssue.PERMISSION
        !channelSound || hasVibrator && !channelVibration -> NotificationIssue.CHANNEL
        !deviceAudible -> NotificationIssue.DEVICE_SOUND
        doNotDisturb -> NotificationIssue.DO_NOT_DISTURB
        else -> null
    }
}

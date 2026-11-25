package com.example.wfappops.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationReadinessTest {
    private val ready = NotificationReadiness(true, true, true, true, true, false)

    @Test
    fun devicesWithoutVibrationHardwareCanBeReadyWithSound() {
        assertNull(ready.copy(hasVibrator = false, channelVibration = false).issue)
        assertEquals(NotificationIssue.CHANNEL, ready.copy(channelVibration = false).issue)
    }

    @Test
    fun permissionIsResolvedBeforeOtherConfigurationProblems() {
        assertEquals(NotificationIssue.PERMISSION,
            ready.copy(allowed = false, channelSound = false, channelVibration = false, deviceAudible = false, doNotDisturb = true).issue)
    }

    @Test
    fun channelConfigurationDoesNotGuaranteeAudibleDevice() {
        assertEquals(NotificationIssue.DEVICE_SOUND, ready.copy(deviceAudible = false).issue)
        assertEquals(NotificationIssue.DO_NOT_DISTURB, ready.copy(doNotDisturb = true).issue)
        assertNull(ready.issue)
    }
}

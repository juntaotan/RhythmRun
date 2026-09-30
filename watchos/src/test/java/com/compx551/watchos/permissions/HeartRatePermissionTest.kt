package com.compx551.watchos.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRatePermissionTest {
    @Test
    fun wearOsFiveAndEarlierUsesBodySensors() {
        assertEquals(
            Manifest.permission.BODY_SENSORS,
            HeartRatePermission.permissionForSdk(35),
        )
    }

    @Test
    fun wearOsSixAndLaterUsesReadHeartRate() {
        assertEquals(
            HeartRatePermission.READ_HEART_RATE,
            HeartRatePermission.permissionForSdk(36),
        )
    }
}

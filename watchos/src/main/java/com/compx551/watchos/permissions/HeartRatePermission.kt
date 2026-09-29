package com.compx551.watchos.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Centralises the platform-specific permission used by Health Services heart-rate APIs. */
object HeartRatePermission {
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

    /** Wear OS 6+ uses the granular health permission; older watches use BODY_SENSORS. */
    fun permissionForCurrentDevice(): String = permissionForSdk(Build.VERSION.SDK_INT)

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permissionForCurrentDevice()) ==
            PackageManager.PERMISSION_GRANTED

    /** True after a denial when an in-context explanation should be shown before retrying. */
    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            permissionForCurrentDevice(),
        )

    internal fun permissionForSdk(sdkInt: Int): String =
        if (sdkInt >= 36) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
}

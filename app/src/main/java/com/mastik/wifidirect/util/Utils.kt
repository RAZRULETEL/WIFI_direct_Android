package com.mastik.wifidirect.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.mastik.wifidirect.MainActivity
import timber.log.Timber
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Utilities class containing static methods
 */
object Utils {

    fun checkWifiDirectPermissions(activity: MainActivity) {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw IllegalStateException("This method can only be called from not the main thread, because it will cause deadlock")

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
            return

        if (
            (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED)
        )
            return

        Timber.tag("Utils").d("Permission not granted")

        var perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms += Manifest.permission.NEARBY_WIFI_DEVICES

        val requestCode = Random.nextInt(Int.MAX_VALUE)

        activity.requestPermissions(
            perms,
            requestCode,
        )

        if (!activity.getPermissionRequestResult())
            exitProcess(1)
    }

    fun checkWifiDirectPermissionsSoft(activity: MainActivity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return true

        if (
            (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED)
        )
            return true

        return false
    }

}
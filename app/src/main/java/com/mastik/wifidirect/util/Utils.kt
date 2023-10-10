package com.mastik.wifidirect.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.mastik.wifidirect.MainActivity
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Utilities class containing static methods
 */
object Utils {

    fun checkWifiDirectPermissions(activity: MainActivity) {
        if (
            ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED)
        )
            return

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

        if(!activity.getPermissionRequestResult())
            exitProcess(1)
    }
}
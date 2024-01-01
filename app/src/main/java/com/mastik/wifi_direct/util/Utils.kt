package com.mastik.wifi_direct.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.mastik.wifi_direct.MainActivity
import com.mastik.wifi_direct.tasks.TaskExecutors
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Utilities class containing static methods
 */
object Utils {

    fun checkWifiDirectPermissions(activity: MainActivity) {
        if (checkWifiDirectPermissionsSoft(activity))
            return

        Timber.tag("Utils").d("Permission not granted")

        var perms: Array<String> = arrayOf()
        perms += if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            Manifest.permission.ACCESS_FINE_LOCATION
        else
            Manifest.permission.NEARBY_WIFI_DEVICES

        activity.requestPermissions.launch(perms)

        TaskExecutors.getCachedPool().execute {
            if (!activity.permissionRequestResultExchanger.exchange(null).values.all { e -> e })
                activity.runOnUiThread {
                    Toast.makeText(activity.applicationContext, "Всего хорошего", Toast.LENGTH_LONG)
                        .show()
                    exitProcess(1)
                }
        }
    }

    fun checkWifiDirectPermissionsSoft(activity: ComponentActivity): Boolean {

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
package com.ali_1200.presence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    fun getRequiredPermissions(isAdvertiser: Boolean = false): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Permissions for newer Android versions
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)

            // Add advertise permission only for advertiser
            if (isAdvertiser) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            // Legacy permissions for older Android versions
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun checkPermissions(
        context: Context,
        permissions: List<String>
    ): List<String> = permissions.filter { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }
}
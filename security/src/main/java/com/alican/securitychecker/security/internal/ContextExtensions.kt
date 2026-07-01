package com.alican.securitychecker.security.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal fun Context.isAppInstalled(packageName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            ).enabled
        } else {
            packageManager.getApplicationInfo(packageName, 0).enabled
        }
    } catch (e: Exception) {
        false
    }
}

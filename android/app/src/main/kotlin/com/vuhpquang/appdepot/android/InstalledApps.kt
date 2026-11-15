package com.vuhpquang.appdepot.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class InstalledApps(private val context: Context) {
    fun versionOf(appId: String): String? = try {
        context.packageManager.getPackageInfo(appId, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun launchIntent(appId: String): Intent? = context.packageManager.getLaunchIntentForPackage(appId)
}

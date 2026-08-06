package com.android.androidAndroid.agent.action.launchApp

import android.content.Context

object InstalledAppResolver {
    @Volatile
    private var cachedApps: List<InstalledApp> = emptyList()

    fun checkAndCacheApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val installed = packageManager.getInstalledApplications(0)
        val result = installed.asSequence()
            .filter { it.packageName != context.packageName }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { app ->
                val label = packageManager.getApplicationLabel(app).toString().trim()
                InstalledApp(
                    appName1 = label,
                    appName2 = null,
                    appName3 = null,
                    packageName = app.packageName
                )
            }
            .filter { it.appName1.isNotBlank() }
            .distinctBy { it.packageName }
            .toList()

        cachedApps = result
        return result
    }

    fun findPackageByAppName(appName: String): String? {
        val normalized = appName.trim().lowercase()
        if (normalized.isEmpty()) return null

        var fuzzy: String? = null
        for (apk in cachedApps) {
            val names = listOf(apk.appName1, apk.appName2, apk.appName3)
                .filterNotNull()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            for (name in names) {
                if (name == normalized) {
                    return apk.packageName
                }
                if (fuzzy == null && (name.contains(normalized) || normalized.contains(name))) {
                    fuzzy = apk.packageName
                }
            }
        }
        return fuzzy
    }
}
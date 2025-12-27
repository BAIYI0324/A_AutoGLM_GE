package com.sidhu.androidautoglm.action

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data class representing installed application information
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isEnabled: Boolean,
    val hasLauncher: Boolean
)

object AppMapper {
    private var predefinedAppMap: Map<String, String> = emptyMap()
    private var dynamicAppMap: Map<String, String> = emptyMap()
    private var isDynamicMappingAvailable = false  // True if dynamic apps were successfully loaded
    private const val CONFIG_FILE_NAME = "app_map.json"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadPredefinedMap()
    }

    private fun loadPredefinedMap() {
        val context = appContext ?: return
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type

        try {
            context.assets.open(CONFIG_FILE_NAME).bufferedReader().use { reader ->
                predefinedAppMap = gson.fromJson(reader, type) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e("AppMapper", "Error loading app map from assets.", e)
            predefinedAppMap = emptyMap()
        }
    }

    /**
     * Get all installed applications from PackageManager
     */
    private fun getInstalledApps(): List<AppInfo> {
        val context = appContext ?: return emptyList()
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return packages.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val label = appInfo.loadLabel(pm).toString()
                val isEnabled = appInfo.enabled
                val hasLauncher = pm.getLaunchIntentForPackage(packageName) != null

                AppInfo(packageName, label, isEnabled, hasLauncher)
            } catch (e: Exception) {
                Log.w("AppMapper", "Error loading app info: ${appInfo.packageName}", e)
                null
            }
        }
    }

    /**
     * Build app name to package name mapping from installed apps
     */
    private fun buildAppMapping(apps: List<AppInfo>): Map<String, String> {
        val mapping = mutableMapOf<String, String>()

        apps.forEach { app ->
            // Skip disabled apps and apps without launch intent
            if (!app.isEnabled || !app.hasLauncher) return@forEach

            // Map app display name to package name
            mapping[app.label] = app.packageName
        }

        return mapping
    }

    /**
     * Refresh the dynamic app mapping from installed apps.
     * This method always refreshes - no cache.
     * Call on background thread (Dispatchers.IO) to avoid blocking.
     */
    fun refreshInstalledApps() {
        val apps = getInstalledApps()
        dynamicAppMap = buildAppMapping(apps)

        // Set flag: dynamic mapping is available only if we got some apps
        isDynamicMappingAvailable = dynamicAppMap.isNotEmpty()

        if (isDynamicMappingAvailable) {
            Log.i("AppMapper", "========== App Mapping Refreshed ==========")
            Log.i("AppMapper", "Total apps discovered: ${dynamicAppMap.size}")
            Log.i("AppMapper", "Using DYNAMIC mapping (predefined mapping disabled)")

            // Print complete app list sorted by app name
            dynamicAppMap.entries
                .sortedBy { it.key }
                .forEach { (appName, packageName) ->
                    Log.i("AppMapper", "  '$appName' → '$packageName'")
                }

            Log.i("AppMapper", "===========================================")
        } else {
            Log.w("AppMapper", "========== Using PREDEFINED Mapping ==========")
            Log.w("AppMapper", "No dynamic apps discovered (permission denied or empty)")
            Log.w("AppMapper", "Falling back to predefined app map (${predefinedAppMap.size} entries)")
            Log.w("AppMapper", "==============================================")
        }
    }

    /**
     * Get package name by app name.
     * Uses dynamic mapping if available, otherwise falls back to predefined mapping.
     */
    fun getPackageName(appName: String): String? {
        Log.d("AppMapper", "Looking up package name for: '$appName'")

        if (isDynamicMappingAvailable) {
            // Use dynamic mapping only
            // 1. Exact match
            dynamicAppMap[appName]?.let {
                Log.i("AppMapper", "✓ Found in DYNAMIC mapping (exact): '$appName' → '$it'")
                return it
            }

            // 2. Case-insensitive match
            val dynamicCaseMatch = dynamicAppMap.entries.find { it.key.equals(appName, ignoreCase = true) }
            if (dynamicCaseMatch != null) {
                Log.i("AppMapper", "✓ Found in DYNAMIC mapping (case-insensitive): '$appName' → '${dynamicCaseMatch.value}' (actual key: '${dynamicCaseMatch.key}')")
                return dynamicCaseMatch.value
            }

            // Not found in dynamic mapping
            Log.w("AppMapper", "✗ NOT FOUND in dynamic mapping: '$appName'")
            return null
        } else {
            // Fallback to predefined mapping (dynamic not available)
            Log.d("AppMapper", "Dynamic mapping not available, using PREDEFINED fallback")

            // 1. Exact match
            predefinedAppMap[appName]?.let {
                Log.i("AppMapper", "✓ Found in PREDEFINED mapping (exact): '$appName' → '$it'")
                return it
            }

            // 2. Case-insensitive match
            val predefinedCaseMatch = predefinedAppMap.entries.find { it.key.equals(appName, ignoreCase = true) }
            if (predefinedCaseMatch != null) {
                Log.i("AppMapper", "✓ Found in PREDEFINED mapping (case-insensitive): '$appName' → '${predefinedCaseMatch.value}' (actual key: '${predefinedCaseMatch.key}')")
                return predefinedCaseMatch.value
            }

            // Not found
            Log.w("AppMapper", "✗ NOT FOUND in predefined mapping: '$appName'")
            return null
        }
    }
}

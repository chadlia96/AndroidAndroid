package com.android.androidAndroid.agent.action.launchApp

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LaunchAppResponseParser {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun parseApk(rawResponse: String, candidates: List<InstalledApp>): InstalledApp? {
        val selectedApp = runCatching {
            val response = mapAdapter.fromJson(stripCodeFence(rawResponse)) ?: return null
            InstalledApp(
                appName1 = response.stringValue("appName"),
                packageName = response.stringValue("packageName")
            )
        }.getOrNull()
            ?.takeIf { it.appName1.isNotEmpty() && it.packageName.isNotEmpty() }
            ?: return null
        return selectedApp.takeIf { selected ->
            candidates.any { candidate ->
                candidate.appName1 == selected.appName1 && candidate.packageName == selected.packageName
            }
        }
    }

    // -- Map 访问辅助,不可为null的String用空字符串兜底 --

    private fun Map<String, Any?>.stringValue(key: String): String {
        return stringOrNull(key) ?: ""
    }

    private fun Map<String, Any?>.stringOrNull(key: String): String? {
        val value = this[key] ?: return null
        val str = value.toString().trim()
        return str.ifBlank { null }
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}

package com.android.androidAndroid.agent.action.describeUi

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal object UiDescriptionResponseParser {

    private val responseAdapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(UiDescription::class.java)

    fun parse(rawResponse: String): UiDescription? {
        val cleaned = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching { responseAdapter.fromJson(cleaned) }
            .getOrNull()
            ?.takeIf { it.summary.isNotBlank() && it.detailDescription.isNotBlank() }
    }
}

internal data class UiDescription(
    val summary: String = "",
    val detailDescription: String = ""
)

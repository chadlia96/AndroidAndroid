package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.agent.model.AgentDecision
import com.android.androidAndroid.agent.model.Position
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object AgentResponseParser {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    private data class RawAction(
        val op: String,
        val target: Any?,
        val text: String?,
        val startPoint: String?,
        val endPoint: String?,
        val appName: String?,
        val appPackage: String?
    ) {
        fun toAction(): AgentAction? {
            return when (op) {
                "click" -> target.toTargetOrNull()?.let(AgentAction::Click)
                "input" -> {
                    val validTarget = target.toTargetOrNull()
                    val validText = text?.takeIf { it.isNotBlank() }
                    if (validTarget != null && validText != null) {
                        AgentAction.Input(validTarget, validText)
                    } else {
                        null
                    }
                }
                "swipe" -> {
                    val validStartPoint = startPoint.toPositionOrNull()
                    val validEndPoint = endPoint.toPositionOrNull()
                    if (validStartPoint != null && validEndPoint != null) {
                        AgentAction.Swipe(validStartPoint, validEndPoint)
                    } else {
                        null
                    }
                }
                "launch_app" -> AgentAction.LaunchApp
                "back" -> AgentAction.Back
                else -> null
            }
        }
    }

    fun parse(raw: String): AgentDecision? {
        val cleaned = stripCodeFence(raw)
        return runCatching {
            val root = mapAdapter.fromJson(cleaned) ?: return null
            val status = root.stringValue("status").lowercase()
            val reason = root.stringOrNull("reason")
            val rawRes = root.stringOrNull("actions")
            val actionsList = root.listValue("actions")
            val actions = actionsList.mapNotNull { item ->
                val obj = item as? Map<*, *> ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val actionMap = obj as Map<String, Any?>
                val rawAction = RawAction(
                    op = actionMap.stringValue("op").lowercase(),
                    target = actionMap["target"],
                    text = actionMap.stringOrNull("text"),
                    startPoint = actionMap.stringOrNull("startPoint"),
                    endPoint = actionMap.stringOrNull("endPoint"),
                    appName = actionMap.stringOrNull("appName"),
                    appPackage = actionMap.stringOrNull("appPackage")
                )
                rawAction.toAction()
            }
            AgentDecision(status = status, agentActions = actions, reason = reason, rawResponse = rawRes)
        }.getOrNull()
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

    private fun Map<String, Any?>.listValue(key: String): List<*> {
        return (this[key] as? List<*>) ?: emptyList<Nothing>()
    }

    private fun Any?.toTargetOrNull(): Int? {
        return when (this) {
            is Number -> {
                val value = toDouble()
                if (!value.isFinite() || value % 1.0 != 0.0 ||
                    value < Int.MIN_VALUE || value > Int.MAX_VALUE
                ) {
                    null
                } else {
                    value.toInt()
                }
            }
            is String -> trim().toIntOrNull()
            else -> null
        }
    }

    private fun String?.toPositionOrNull(): Position? {
        val match = this?.trim()?.let(COORDINATE_PATTERN::matchEntire) ?: return null
        val x = match.groupValues[1].toFloatOrNull()
        val y = match.groupValues[2].toFloatOrNull()
        if (x == null || y == null || !x.isFinite() || !y.isFinite()) return null
        return Position(x, y)
    }

    private val COORDINATE_PATTERN = Regex("(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)")

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val body = trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
        return body.trim()
    }
}
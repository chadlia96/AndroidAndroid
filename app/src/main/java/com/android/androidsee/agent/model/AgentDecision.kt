package com.android.androidAndroid.agent.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class AgentDecision(
    val status: String,
    val agentActions: List<AgentAction>,
    val reason: String? = null,
    val rawResponse: String? = null,

)

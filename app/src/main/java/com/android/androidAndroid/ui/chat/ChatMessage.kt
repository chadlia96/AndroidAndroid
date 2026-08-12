package com.android.androidAndroid.ui.chat

data class ChatMessage(
    val message: String,
    val mode: Int = 0,
    val time: String?,
)
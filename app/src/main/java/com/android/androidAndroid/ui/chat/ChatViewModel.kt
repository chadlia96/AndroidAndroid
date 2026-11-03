package com.android.androidAndroid.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.androidAndroid.accessibility.NERV
import com.android.androidAndroid.llm.LLMChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel : ViewModel() {

    private val entriesState = MutableLiveData(mutableListOf<ChatMessage>())
    private val selectedImageUriState = MutableLiveData<Uri?>(null)

    val entries: LiveData<MutableList<ChatMessage>> = entriesState
    val selectedImageUri: LiveData<Uri?> = selectedImageUriState

    var entryMode: Int = ENTRY_MODE_AGENT_LOOP
        private set

    fun setEntryMode(mode: Int) {
        entryMode = mode
    }

    fun setSelectedImageUri(uri: Uri?) {
        selectedImageUriState.value = uri
    }

    fun submitInput(context: Context, rawInput: String): Boolean {
        val imageUri = selectedImageUriState.value
        if (rawInput.isBlank() && imageUri == null) return false

        val normalizedInput = rawInput.ifBlank { "图片解析" }
        appendEntry(
            ChatMessage(
                message = normalizedInput,
                mode = 0,
                time = nowTimeText()
            )
        )

        if (entryMode == ENTRY_MODE_JUST_TALK) {
            launchSimpleChatReply(context.applicationContext, normalizedInput, imageUri)
        } else {
            launchAgentLoopReply(normalizedInput)
            selectedImageUriState.value = null
        }

        return true
    }

    private fun appendEntry(entry: ChatMessage) {
        val entries = entriesState.value ?: mutableListOf()
        entries.add(entry)
        entriesState.postValue(entries)
    }

    private fun launchAgentLoopReply(message: String) {
        NERV.submitUserInput(message)
    }

    private fun launchSimpleChatReply(
        context: Context,
        message: String,
        imageUri: Uri?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (imageUri != null) {
                LLMChat.deepseekReadImage(context, imageUri, message.trim().ifBlank{ "请形容图片内容" })
            } else {
                LLMChat.deepseekChat(message, false)
            }
            selectedImageUriState.postValue(null)
            appendEntry(
                ChatMessage(
                    message = result,
                    mode = 1,
                    time = nowTimeText()
                )
            )
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    fun nowTimeText(): String {
        return timeFormat.format(Date())
    }

    companion object {
        private const val ENTRY_MODE_AGENT_LOOP = 1
        private const val ENTRY_MODE_JUST_TALK = 2
    }
}

package com.android.androidAndroid.llm

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.android.androidAndroid.logging.AppLogger as Log
import com.android.androidAndroid.BuildConfig
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.CancellationException

object LLMChat {

    private const val TAG = "LLMChat"
    private const val LLM_TRACE_TAG = "LLM_TRACE"
    private const val DEEPSEEK_URL = "https://api.deepseek.com"
    private const val DEEPSEEK_MODEL = "deepseek-v4-flash"
    private const val MOONSHOT_URL = "https://api.moonshot.cn/v1"
    private const val MOONSHOT_MODEL = "moonshot-v1-8k-vision-preview"

    private val deepSeekClient = OpenAIOkHttpClient.builder()
        .apiKey(BuildConfig.DEEPSEEK_API_KEY)
        .baseUrl(DEEPSEEK_URL)
        .maxRetries(3)
        .build()

    private val moonshotClient by lazy {
        OpenAIOkHttpClient.builder()
            .apiKey(BuildConfig.MOONSHOT_API_KEY)
            .baseUrl(MOONSHOT_URL)
            .maxRetries(3)
            .build()
    }

    suspend fun deepseekChat(prompt: String, jsonOutput: Boolean): String {
        Log.d(LLM_TRACE_TAG, "DEEPSEEK_PROMPT(jsonOutput=$jsonOutput):\n$prompt")
        return try {
            val bodyProperties = mutableMapOf(
                "stream" to JsonValue.from(false),
                "reasoning_effort" to JsonValue.from("high"),
                "thinking" to JsonValue.from(mapOf("type" to "enabled"))
            )
            if (jsonOutput) {
                bodyProperties["response_format"] = JsonValue.from(mapOf("type" to "json_object"))
            }

            val response = deepSeekClient.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(DEEPSEEK_MODEL)
                    .addUserMessage(prompt)
                    .additionalBodyProperties(bodyProperties)
                    .build()
            )
            response.choices().firstOrNull()?.message()?.content()?.orElse(null)
                ?.takeIf { it.isNotBlank() }
                ?.also { Log.d(LLM_TRACE_TAG, "DEEPSEEK_RESPONSE:\n$it") }
                ?: error("DeepSeek 返回空响应.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("DeepSeek", error)
        }
    }

    suspend fun moonshotReadImage(context: Context, uri: Uri, prompt: String): String {
        return try {
            val mimeType = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "image/png"
            val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取图片内容.")
            moonshotReadImage(imageBytes, mimeType, prompt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("Moonshot 图片识别", error)
        }
    }

    suspend fun moonshotReadImage(
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String
    ): String {
        Log.d(LLM_TRACE_TAG, "MOONSHOT_IMAGE_PROMPT:\n$prompt")
        return try {
            require(imageBytes.isNotEmpty()) { "图片内容为空." }
            val imageUrl = "data:$mimeType;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}"
            val imagePart = ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                    .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(imageUrl).build())
                    .build()
            )
            val textPart = ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder().text(prompt).build()
            )
            val response = moonshotClient.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(MOONSHOT_MODEL)
                    .addSystemMessage("你是 Kimi.")
                    .addUserMessageOfArrayOfContentParts(listOf(imagePart, textPart))
                    .build()
            )
            response.choices().firstOrNull()?.message()?.content()?.orElse(null)
                ?.takeIf { it.isNotBlank() }
                ?.also { Log.d(LLM_TRACE_TAG, "MOONSHOT_IMAGE_RESPONSE:\n$it") }
                ?: error("Moonshot 返回空响应.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("Moonshot 图片识别", error)
        }
    }

    private fun handleError(providerName: String, error: Exception): String {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        Log.e(TAG, "$providerName 调用失败: $detail", error)
        return "$providerName 调用失败: $detail".also {
            Log.e(LLM_TRACE_TAG, "RESPONSE_ERROR:\n$it")
        }
    }
}

package com.android.androidAndroid.llm

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.android.androidAndroid.logging.AppLogger as Log
import com.android.androidAndroid.BuildConfig
import com.android.androidAndroid.util.ImageCompressor
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
    private const val DEEPSEEK_IMAGE_MODEL = "deepseek-flash"

    private val deepSeekClient = OpenAIOkHttpClient.builder()
        .apiKey(BuildConfig.DEEPSEEK_API_KEY)
        .baseUrl(DEEPSEEK_URL)
        .maxRetries(3)
        .build()

    suspend fun deepseekChat(
        prompt: String,
        jsonOutput: Boolean,
        imageBytes: ByteArray? = null
    ): String {
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

            val request = ChatCompletionCreateParams.builder()
                .model(DEEPSEEK_MODEL)
                .additionalBodyProperties(bodyProperties)
            if (imageBytes == null) {
                request.addUserMessage(prompt)
            } else {
                val jpegBytes = ImageCompressor.compressToJpeg(imageBytes)
                val imageUrl = "data:image/jpeg;base64,${Base64.encodeToString(jpegBytes, Base64.NO_WRAP)}"
                val imagePart = ChatCompletionContentPart.ofImageUrl(
                    ChatCompletionContentPartImage.builder()
                        .imageUrl(
                            ChatCompletionContentPartImage.ImageUrl.builder()
                                .url(imageUrl)
                                .detail(ChatCompletionContentPartImage.ImageUrl.Detail.of("original"))
                                .build()
                        )
                        .build()
                )
                val textPart = ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder().text(prompt).build()
                )
                request.addUserMessageOfArrayOfContentParts(listOf(textPart, imagePart))
            }
            val response = deepSeekClient.chat().completions().create(request.build())
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

    suspend fun deepseekReadImage(context: Context, uri: Uri, prompt: String): String {
        return try {
            deepseekReadCompressedImage(ImageCompressor.compressUri(context, uri), prompt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("DeepSeek 图片识别", error)
        }
    }

    suspend fun deepseekReadImage(
        imageBytes: ByteArray,
        prompt: String
    ): String {
        return try {
            deepseekReadCompressedImage(ImageCompressor.compressToJpeg(imageBytes), prompt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("DeepSeek 图片识别", error)
        }
    }

    private suspend fun deepseekReadCompressedImage(imageBytes: ByteArray, prompt: String): String {
        Log.d(LLM_TRACE_TAG, "DEEPSEEK_IMAGE_PROMPT:\n$prompt")
        return try {
            val imageUrl = "data:image/jpeg;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}"
            val imagePart = ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                    .imageUrl(
                        ChatCompletionContentPartImage.ImageUrl.builder()
                            .url(imageUrl)
                            .detail(ChatCompletionContentPartImage.ImageUrl.Detail.of("original"))
                            .build()
                    )
                    .build()
            )
            val textPart = ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder().text(prompt).build()
            )
            val response = deepSeekClient.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(DEEPSEEK_IMAGE_MODEL)
                    .addUserMessageOfArrayOfContentParts(listOf(textPart, imagePart))
                    .build()
            )
            response.choices().firstOrNull()?.message()?.content()?.orElse(null)
                ?.takeIf { it.isNotBlank() }
                ?.also { Log.d(LLM_TRACE_TAG, "DEEPSEEK_IMAGE_RESPONSE:\n$it") }
                ?: error("DeepSeek 返回空响应.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError("DeepSeek 图片识别", error)
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

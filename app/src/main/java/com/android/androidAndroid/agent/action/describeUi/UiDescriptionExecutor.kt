package com.android.androidAndroid.agent.action.describeUi

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import com.android.androidAndroid.logging.AppLogger as Log
import android.view.Display
import com.android.androidAndroid.accessibility.NERV
import com.android.androidAndroid.agent.model.ActionExecutionResult
import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.agent.model.DescribeUiActionExecutionResult
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import com.android.androidAndroid.llm.LLMChat
import com.android.androidAndroid.ui.overlay.FloatingButtonsOverlayHelper
import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object UiDescriptionExecutor {

    suspend fun execute(): ActionExecutionResult {
        val service = NERV.getRunningService()
            ?: return failure("无障碍服务不可用")
        val buttonsWereShowing = FloatingButtonsOverlayHelper.isShowing()
        val textWasShowing = FloatingTextOverlayHelper.isShowing()

        return try {
            setOverlaysVisible(false)
            delay(OVERLAY_HIDE_DELAY_MS)
            val imageBytes = capturePng(service)
                ?: return failure("屏幕截图失败")
            val rawResponse = LLMChat.moonshotReadImage(
                imageBytes,
                PNG_MIME_TYPE,
                AgentPromptBuilder.DESCRIBE_UI_PROMPT
            )
            val description = UiDescriptionResponseParser.parse(rawResponse)
                ?: return failure("视觉模型未返回有效的UI描述")
            DescribeUiActionExecutionResult(
                agentAction = AgentAction.DescribeUi,
                target = "",
                success = true,
                message = description.summary.trim(),
                detailDescription = description.detailDescription.trim()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "UI视觉探测失败: ${error.message.orEmpty()}", error)
            failure("UI视觉探测失败: ${error.message.orEmpty()}")
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (buttonsWereShowing) FloatingButtonsOverlayHelper.setCaptureVisible(true)
                if (textWasShowing) FloatingTextOverlayHelper.setCaptureVisible(true)
            }
        }
    }

    private suspend fun setOverlaysVisible(visible: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            FloatingButtonsOverlayHelper.setCaptureVisible(visible)
            FloatingTextOverlayHelper.setCaptureVisible(visible)
        }
    }

    private suspend fun capturePng(service: NERV): ByteArray? {
        return suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val imageBytes = screenshotToPng(screenshot)
                        if (continuation.isActive) continuation.resume(imageBytes)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "屏幕截图失败, errorCode=$errorCode")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    }

    private fun screenshotToPng(
        screenshot: AccessibilityService.ScreenshotResult
    ): ByteArray? {
        return screenshot.hardwareBuffer.use { hardwareBuffer ->
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                ?: return@use null
            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                ByteArrayOutputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) null
                    else output.toByteArray()
                }
            } finally {
                bitmap.recycle()
                hardwareBitmap.recycle()
            }
        }
    }

    private fun failure(message: String): ActionExecutionResult {
        return ActionExecutionResult(
            agentAction = AgentAction.DescribeUi,
            target = "",
            success = false,
            message = message
        )
    }

    private const val TAG = "UiDescriptionExecutor"
    private const val PNG_MIME_TYPE = "image/png"
    private const val OVERLAY_HIDE_DELAY_MS = 100L
}

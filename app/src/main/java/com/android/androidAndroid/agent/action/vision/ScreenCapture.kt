package com.android.androidAndroid.agent.action.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import com.android.androidAndroid.logging.AppLogger as Log
import android.view.Display
import com.android.androidAndroid.accessibility.NERV
import com.android.androidAndroid.ui.overlay.FloatingButtonsOverlayHelper
import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object ScreenCapture {

    suspend fun capture(): ByteArray {
        val service = NERV.getRunningService()
            ?: error("无障碍服务不可用")
        val buttonsWereShowing = FloatingButtonsOverlayHelper.isShowing()
        val textWasShowing = FloatingTextOverlayHelper.isShowing()

        return try {
            setOverlaysVisible(false)
            delay(OVERLAY_HIDE_DELAY_MS)
            capturePng(service) ?: error("屏幕截图失败")
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

    private const val TAG = "ScreenCapture"
    private const val OVERLAY_HIDE_DELAY_MS = 100L
}

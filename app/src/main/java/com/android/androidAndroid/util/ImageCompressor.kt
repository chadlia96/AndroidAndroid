package com.android.androidAndroid.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import com.android.androidAndroid.logging.AppLogger as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val IMAGE_MAX_SHORT_SIDE = 1440
    private const val IMAGE_MAX_LONG_SIDE = 3200
    private const val JPEG_QUALITY = 90

    suspend fun compressUri(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片内容.")
        compressImage(imageBytes)
    }

    suspend fun compressToJpeg(imageBytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        compressImage(imageBytes)
    }

    private fun compressImage(imageBytes: ByteArray): ByteArray {
        require(imageBytes.isNotEmpty()) { "图片内容为空." }
        val startedAt = SystemClock.elapsedRealtime()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片内容." }

        val inputWidth = bounds.outWidth
        val inputHeight = bounds.outHeight
        val shortSide = min(inputWidth, inputHeight)
        val longSide = max(inputWidth, inputHeight)
        val scale = min(
            1f,
            min(IMAGE_MAX_SHORT_SIDE.toFloat() / shortSide, IMAGE_MAX_LONG_SIDE.toFloat() / longSide)
        )
        val outputWidth = (inputWidth * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (inputHeight * scale).roundToInt().coerceAtLeast(1)
        val sampleSize = calculateSampleSize(inputWidth, inputHeight, outputWidth, outputHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: error("无法解码图片内容.")
        var scaledBitmap: Bitmap? = null
        var jpegBitmap: Bitmap? = null
        try {
            val resizedBitmap = if (decodedBitmap.width == outputWidth && decodedBitmap.height == outputHeight) {
                decodedBitmap
            } else {
                Bitmap.createScaledBitmap(decodedBitmap, outputWidth, outputHeight, true).also { scaledBitmap = it }
            }
            jpegBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            Canvas(jpegBitmap!!).apply {
                drawColor(Color.WHITE)
                drawBitmap(resizedBitmap, 0f, 0f, null)
            }
            val jpegBytes = ByteArrayOutputStream().use { output ->
                check(jpegBitmap!!.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) { "图片压缩失败." }
                output.toByteArray()
            }
            Log.d(
                TAG,
                "DEEPSEEK_IMAGE_COMPRESS: ${inputWidth}x${inputHeight}/${imageBytes.size} bytes -> " +
                    "${outputWidth}x${outputHeight}/${jpegBytes.size} bytes, " +
                    "${SystemClock.elapsedRealtime() - startedAt} ms"
            )
            return jpegBytes
        } finally {
            jpegBitmap?.recycle()
            scaledBitmap?.takeIf { it !== decodedBitmap }?.recycle()
            decodedBitmap.recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetWidth && height / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

package com.android.androidAndroid.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.android.androidAndroid.databinding.LayoutFloatingTextOverlayBinding
import java.lang.ref.WeakReference

object FloatingTextOverlayHelper {

    private const val TAG = "FloatingTextOverlayHelper"
    private var contextRef: WeakReference<Context>? = null
    @Volatile
    private var binding: LayoutFloatingTextOverlayBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (contextRef?.get() != null) return
        contextRef = WeakReference(context.applicationContext)
    }

    fun show(tag: String = TAG) {
        if (binding != null) return

        val safeContext = contextRef?.get()
        if (safeContext == null) {
            Log.w(tag, "悬浮窗未初始化")
            return
        }
        ensureOverlayPermission(safeContext)
        mainHandler.post {
            val wm = safeContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm == null) {
                Log.e(tag, "悬浮窗创建失败: 无法获取 WindowManager")
                return@post
            }

            val newBinding =
                LayoutFloatingTextOverlayBinding.inflate(LayoutInflater.from(safeContext))
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(56)
                y = dpToPx(180)
            }

            runCatching {
                wm.addView(newBinding.root, params)
                binding = newBinding
            }
        }

    }

    fun setLoading(isShow: Boolean) {
        mainHandler.post {
            binding?.tvOverlayLoading?.visibility = if (isShow) View.VISIBLE else View.INVISIBLE
        }
    }

    fun isThinking(isThinking: Boolean) {
        mainHandler.post {
            binding?.let {
                it.tvOverlayLoading.visibility = if (isThinking) View.VISIBLE else View.INVISIBLE
                it.tvOverlayTitle.text = if (isThinking) "正在思考" else ""
            }
        }
    }

    fun setTitle(title: String): Boolean {
        mainHandler.post { binding?.tvOverlayTitle?.text = title }
        return isShowing()
    }

    fun setLog(log: String): Boolean {
        mainHandler.post { binding?.tvOverlayLog?.text = log }
        return isShowing()
    }

    fun isShowing(): Boolean = binding != null

    fun release(): Boolean {
        val currentBinding = binding
        binding = null
        val result = runCatching {
            if (currentBinding != null) {
                contextRef?.get()
                    ?.getSystemService(Context.WINDOW_SERVICE)
                    ?.let { it as? WindowManager }
                    ?.removeViewImmediate(currentBinding.root)
            }
            true
        }.getOrElse {
            Log.w(TAG, "悬浮窗释放失败: ${it.message}")
            false
        }
        contextRef = null
        return result
    }

    private fun ensureOverlayPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(context)
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dpToPx(value: Int): Int {
        val safeContext = contextRef?.get() ?: return value
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            safeContext.resources.displayMetrics
        ).toInt()
    }
}

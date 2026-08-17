package com.android.androidAndroid.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import com.android.androidAndroid.logging.AppLogger as Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.android.androidAndroid.MainActivity
import com.android.androidAndroid.databinding.LayoutFloatingButtonsOverlayBinding
import java.lang.ref.WeakReference

object FloatingButtonsOverlayHelper {

    private const val TAG = "FloatingButtonsOverlayHelper"
    private var contextRef: WeakReference<Context>? = null
    private var binding: LayoutFloatingButtonsOverlayBinding? = null
    private var click1: View.OnClickListener? = null
    private var click2: View.OnClickListener? = null
    private var click3: View.OnClickListener? = null

    fun init(context: Context) {
        if (contextRef?.get() != null) return
        contextRef = WeakReference(context.applicationContext)
        click3 = View.OnClickListener {
            contextRef?.get()?.startActivity(
                Intent(contextRef?.get(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
            )
        }
    }

    fun show(tag: String = TAG): Boolean {
        if (binding != null) return true

        val safeContext = contextRef?.get()
        if (safeContext == null) {
            Log.w(tag, "悬浮窗未初始化")
            return false
        }
        if (!ensureOverlayPermission(safeContext)) return false

        val wm = safeContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            Log.e(tag, "悬浮窗创建失败: 无法获取 WindowManager")
            return false
        }

        val newBinding = LayoutFloatingButtonsOverlayBinding.inflate(
            LayoutInflater.from(safeContext)
        ).apply {
            btn1.setOnClickListener { click1?.onClick(it) }
            btn2.setOnClickListener { click2?.onClick(it) }
            btn3.setOnClickListener { click3?.onClick(it) }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(180)
        }

        return runCatching {
            wm.addView(newBinding.root, params)
            binding = newBinding
            true
        }.getOrElse {
            Log.e(tag, "悬浮窗创建失败: ${it.message}")
            false
        }
    }

    fun setClick1(onClick: View.OnClickListener?): Boolean {
        click1 = onClick
        return isShowing()
    }

    fun setClick2(onClick: View.OnClickListener?): Boolean {
        click2 = onClick
        return isShowing()
    }

    fun setClick3(onClick: View.OnClickListener?): Boolean {
        click3 = onClick
        return isShowing()
    }

    fun clear1(): Boolean {
        click1 = null
        return true
    }

    fun clear2(): Boolean {
        click2 = null
        return true
    }

    fun clear3(): Boolean {
        click3 = null
        return true
    }

    fun clearAll(): Boolean {
        click1 = null
        click2 = null
        click3 = null
        return true
    }

    fun isShowing(): Boolean = binding != null

    fun setCaptureVisible(visible: Boolean) {
        binding?.root?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun release(): Boolean {
        val currentBinding = binding
        binding = null
        clearAll()
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

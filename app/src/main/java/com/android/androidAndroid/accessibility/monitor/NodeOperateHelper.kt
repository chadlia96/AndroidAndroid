package com.android.androidAndroid.accessibility.monitor

import com.android.androidAndroid.logging.AppLogger as Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.androidAndroid.accessibility.NERV
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.accessibility.AccessibilityNodeMapper
import java.lang.ref.WeakReference

object NodeOperateHelper {

    private const val TAG = "NodeOperateHelper"

    @Volatile
    private var serviceRef: WeakReference<NERV>? = null

    @Volatile
    private var lastDumpTimeMs: Long = 0L

    @Volatile
    private var lastEventTimeMs: Long = System.currentTimeMillis()

    @Volatile
    var lastEventText: String = ""

    @Volatile
    var isEventTextChanged: Boolean = false

    val isPrint = false

    fun attach(service: NERV) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: NERV) {
        val current = serviceRef?.get()
        if (current === service) {
            serviceRef = null
        }
    }

    fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.i(TAG,event.toString())
            val eventText = event.text
                .map { it.toString().trim() }
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
            if (eventText.isNotBlank()) {
                lastEventText = eventText
                isEventTextChanged = true
            }
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        // 界面变化时刷新时间戳,供 AgentLoop 判断界面是否已稳定
        lastEventTimeMs = System.currentTimeMillis()
    }


    fun getSnapshot(root: AccessibilityNodeInfo): List<ScreenNode> {
        val result = mutableListOf<ScreenNode>()
        AccessibilityNodeMapper.traverseNodeTree(root, result)
        return result
    }

    @Suppress("DEPRECATION")
    fun getCurrentAppInfo(root: AccessibilityNodeInfo): String {
        val packageName = root.packageName?.toString()?.trim().orEmpty()
        if (packageName.isBlank()) {
            return ""
        }

        val appName = serviceRef?.get()?.packageManager?.let { packageManager ->
            runCatching {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString().trim()
            }.getOrNull()
        }
        if(appName != null) return "\n当前APP:$appName.包名:$packageName"
        return "\n包名:$packageName"
    }

    fun consumeToastText() = if(isEventTextChanged){
        isEventTextChanged = false
        lastEventText
    } else ""

    fun isUiStable(stableDurationMs: Long): Boolean {
        return System.currentTimeMillis() - lastEventTimeMs >= stableDurationMs
    }

    fun markUiChanged() {
        lastEventTimeMs = System.currentTimeMillis()
    }

    fun getCurrentRootCopy(): AccessibilityNodeInfo? {
        val service = serviceRef?.get() ?: return null
        val root = service.rootInActiveWindow ?: return null
        return try {
            AccessibilityNodeInfo.obtain(root)
        } finally {
            root.recycle()
        }
    }

}

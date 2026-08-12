package com.android.androidAndroid.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.android.androidAndroid.agent.AgentLoop
import com.android.androidAndroid.agent.TaskWatcher
import com.android.androidAndroid.agent.action.launchApp.InstalledApp
import com.android.androidAndroid.ui.overlay.FloatingButtonsOverlayHelper
import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper
import com.android.androidAndroid.accessibility.monitor.NodeOperateHelper
import com.android.androidAndroid.agent.action.launchApp.InstalledAppResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class NERV : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var installedApps: List<InstalledApp>

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentServiceRef = WeakReference(this)
        NodeOperateHelper.attach(this)
        installedApps = InstalledAppResolver.checkAndCacheApps(this)
        FloatingButtonsOverlayHelper.init(applicationContext)
        FloatingTextOverlayHelper.init(applicationContext)
        FloatingButtonsOverlayHelper.setClick2 {
            dumpRenderedCurrentWindow()
        }
        if (!FloatingButtonsOverlayHelper.show()) {
            Log.w(TAG, "悬浮窗未显示,可能缺少悬浮窗权限")
        }
        Log.i(TAG, "NERV 已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        NodeOperateHelper.handleAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    private fun dumpRenderedCurrentWindow() {
        val root = NodeOperateHelper.getCurrentRootCopy()
        if (root == null) {
            Log.w(TAG, "VIEW_NODE_DUMP: 无法获取当前界面信息")
            return
        }
        try {
            AccessibilityNodeMapper.dumpNodeTree(root, TAG)
        } finally {
            root.recycle()
        }
    }

    override fun onDestroy() {
        if (currentServiceRef?.get() === this) {
            currentServiceRef = null
        }
        NodeOperateHelper.detach(this)
        TaskWatcher.release()
        FloatingButtonsOverlayHelper.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun submitUserInputInternal(input: String): Boolean {
        serviceScope.launch(Dispatchers.IO) {
            AgentLoop.send(input)
        }
        return true
    }

    companion object {
        private const val TAG = "NERV"
        @Volatile
        private var currentServiceRef: WeakReference<NERV>? = null

        fun getRunningService(): NERV? {
            return currentServiceRef?.get()
        }

        fun submitUserInput(input: String): Boolean {
            return getRunningService()?.submitUserInputInternal(input) ?: false
        }

        fun stopAndClean() {
            TaskWatcher.release()
            FloatingButtonsOverlayHelper.release()
            getRunningService()?.disableSelf()
        }
    }
}

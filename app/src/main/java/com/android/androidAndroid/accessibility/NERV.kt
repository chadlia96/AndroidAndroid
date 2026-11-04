package com.android.androidAndroid.accessibility

import android.accessibilityservice.AccessibilityService
import com.android.androidAndroid.logging.AppLogger as Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    private fun submitUserInputInternal(input: String): Flow<String> = flow {
        // 任务由服务持有,订阅结束只释放输出通道,不终止正在执行的任务.
        val output = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val task = serviceScope.launch(Dispatchers.IO) {
            TaskWatcher.bindOutput(output)
            try {
                TaskWatcher.finish(AgentLoop.send(input))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "任务执行失败", error)
                output.close(error)
            } finally {
                TaskWatcher.unbindOutput(output)
            }
        }
        task.invokeOnCompletion { error -> output.close(error) }
        emitAll(output.consumeAsFlow())
    }

    companion object {
        private const val TAG = "NERV"
        @Volatile
        private var currentServiceRef: WeakReference<NERV>? = null

        fun getRunningService(): NERV? {
            return currentServiceRef?.get()
        }

        fun submitUserInput(input: String): Flow<String> {
            return getRunningService()?.submitUserInputInternal(input)
                ?: flowOf("无障碍服务未连接,无法执行任务")
        }

        fun stopAndClean() {
            TaskWatcher.release()
            FloatingButtonsOverlayHelper.release()
            getRunningService()?.disableSelf()
        }
    }
}

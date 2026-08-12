package com.android.androidAndroid.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.android.androidAndroid.BuildConfig
import com.android.androidAndroid.accessibility.monitor.NodeOperateHelper
import com.android.androidAndroid.agent.action.launchApp.LaunchAppResponseParser
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.agent.action.launchApp.InstalledAppResolver
import com.android.androidAndroid.llm.LLMChat

object AccessibilityActionExecutor {

    suspend fun execute(
        agentAction: AgentAction,
        targetNode: ScreenNode?,
        goal: String
    ): NodeActionExecutionResult {
        return when (agentAction) {
            is AgentAction.Click -> executeClick(targetNode)
            is AgentAction.Input -> executeInput(targetNode, agentAction.text)
            is AgentAction.Swipe -> executeSwipe(agentAction)
            AgentAction.Back -> executeBack()
            AgentAction.LaunchApp -> executeLaunchApp(goal)
        }
    }

    private suspend fun executeLaunchApp(goal: String): NodeActionExecutionResult {
        val service = NERV.getRunningService()
            ?: return NodeActionExecutionResult(false, "无障碍服务不可用")
        val apps = InstalledAppResolver.checkAndCacheApps(service)
        if (apps.isEmpty()) return NodeActionExecutionResult(false, "没有可启动的应用")

        val rawResponse = runCatching {
            LLMChat.deepseekChat(AgentPromptBuilder.buildLaunchAppPrompt(goal, apps), true)
        }.getOrElse {
            return NodeActionExecutionResult(false, "应用选择请求失败: ${it.message.orEmpty()}")
        }
        val app = LaunchAppResponseParser.parseApk(rawResponse, apps)
            ?: return NodeActionExecutionResult(false, "未选择有效的候选应用")
        val intent = service.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return NodeActionExecutionResult(false, "未找到应用启动入口", app.appName1)

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            NodeActionExecutionResult(true, "已请求启动应用: ${app.appName1}", app.appName1)
        }.getOrElse {
            NodeActionExecutionResult(false, "启动应用失败: ${it.message.orEmpty()}", app.appName1)
        }
    }

    private fun executeClick(targetNode: ScreenNode?): NodeActionExecutionResult {
        return ClickExecutor.executeClick(targetNode)
    }

    private fun executeInput(targetNode: ScreenNode?, text: String): NodeActionExecutionResult {
        return executeOnTargetNode(targetNode) { node ->
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    private fun executeSwipe(agentAction: AgentAction.Swipe): NodeActionExecutionResult {
        val service = NERV.getRunningService()
            ?: return NodeActionExecutionResult(false, "无障碍服务不可用")
        return runCatching {
            val path = Path().apply {
                moveTo(agentAction.startPoint.x, agentAction.startPoint.y)
                lineTo(agentAction.endPoint.x, agentAction.endPoint.y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
                .build()
            if (service.dispatchGesture(gesture, null, null)) {
                NodeActionExecutionResult(true, "滑动手势已发送")
            } else {
                NodeActionExecutionResult(false, "滑动手势发送失败")
            }
        }.getOrElse {
            NodeActionExecutionResult(false, "滑动手势异常: ${it.message.orEmpty()}")
        }
    }

    private fun executeBack(): NodeActionExecutionResult {
        val service = NERV.getRunningService()
            ?: return NodeActionExecutionResult(false, "无障碍服务不可用")
        return runCatching {
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                NodeActionExecutionResult(true, "返回操作已发送")
            } else {
                NodeActionExecutionResult(false, "返回操作发送失败")
            }
        }.getOrElse {
            NodeActionExecutionResult(false, "返回操作异常: ${it.message.orEmpty()}")
        }
    }

    private fun executeOnTargetNode(
        targetNode: ScreenNode?,
        operation: (AccessibilityNodeInfo) -> Boolean
    ): NodeActionExecutionResult {
        if (targetNode == null) return NodeActionExecutionResult(false, "目标节点不存在")

        val root = NodeOperateHelper.getCurrentRootCopy()
            ?: return NodeActionExecutionResult(false, "无法获取当前界面")
        try {
            val node = findTargetNode(root, targetNode)
            if (node == null) {
                dumpFailureTree(root)
                return NodeActionExecutionResult(false, "未在当前界面找到目标节点")
            }
            try {
                val success = runCatching { operation(node) }.getOrElse {
                    dumpFailureTree(node)
                    return NodeActionExecutionResult(false, "节点操作异常: ${it.message.orEmpty()}")
                }
                return if (success) {
                    NodeActionExecutionResult(true, "节点操作已发送")
                } else {
                    val cn = node.className?.toString().orEmpty()
                    dumpFailureTree(node)
                    NodeActionExecutionResult(false, "节点操作发送失败(class=$cn)")
                }
            } finally {
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    // 节点操作失败或异常时,从错误节点的父节点的父节点开始 dump 原始节点树,便于定位问题。
    internal fun dumpFailureTree(errorNode: AccessibilityNodeInfo) {
        if (!DEBUG) return
        var parent: AccessibilityNodeInfo? = null
        var grandparent: AccessibilityNodeInfo? = null
        try {
            parent = errorNode.parent
            grandparent = parent?.parent
            // 父两层不足时逐级回退,保证至少能输出一层有效子树。
            val dumpRoot = grandparent ?: parent ?: errorNode
            Log.i(
                DUMP_TAG,
                "失败节点 dump(class=${errorNode.className}, " +
                    "起始层级=${if (grandparent != null) 2 else if (parent != null) 1 else 0})"
            )
            AccessibilityNodeMapper.dumpNodeTree(dumpRoot, DUMP_TAG)
        } catch (t: Throwable) {
            Log.w(DUMP_TAG, "失败节点 dump 异常: ${t.message.orEmpty()}")
        } finally {
            parent?.recycle()
            grandparent?.recycle()
        }
    }

    internal fun findTargetNode(
        node: AccessibilityNodeInfo,
        targetNode: ScreenNode
    ): AccessibilityNodeInfo? {
        if (AccessibilityNodeMapper.buildSnapshotNode(node) == targetNode) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val foundNode = findTargetNode(child, targetNode)
                if (foundNode != null) return foundNode
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private const val SWIPE_DURATION_MS = 300L
    // 调试开关,跟随构建类型,release 构建自动关闭。
    private val DEBUG = BuildConfig.DEBUG
    private const val DUMP_TAG = "ACTION_DUMP"
}

data class NodeActionExecutionResult(
    val success: Boolean,
    val message: String,
    val target: String = ""
)

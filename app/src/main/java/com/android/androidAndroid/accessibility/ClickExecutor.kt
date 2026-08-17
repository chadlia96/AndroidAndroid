package com.android.androidAndroid.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.accessibility.monitor.NodeOperateHelper

object ClickExecutor {

    // 点击执行入口:调试期间优先按节点中心坐标执行触摸点击,
    // 触摸无法派发时保留原有 ACTION_CLICK 路径作为兜底。
    fun executeClick(targetNode: ScreenNode?): NodeActionExecutionResult {
        if (targetNode == null) return NodeActionExecutionResult(false, "目标节点不存在")

        val root = NodeOperateHelper.getCurrentRootCopy()
            ?: return NodeActionExecutionResult(false, "无法获取当前界面")
        try {
            val node = AccessibilityActionExecutor.findTargetNode(root, targetNode)
            if (node == null) {
                AccessibilityActionExecutor.dumpFailureTree(root)
                return NodeActionExecutionResult(false, "未在当前界面找到目标节点")
            }
            try {
                return executeClickOnNode(node)
            } catch (t: Throwable) {
                AccessibilityActionExecutor.dumpFailureTree(node)
                return NodeActionExecutionResult(false, "节点操作异常: ${t.message.orEmpty()}")
            } finally {
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun executeClickOnNode(node: AccessibilityNodeInfo): NodeActionExecutionResult {
        // 调试逻辑:所有点击默认在目标节点中心点模拟触摸。
        val service = NERV.getRunningService()
        val center = service?.let { TouchUtils.centerOf(node) }
        if (service != null && center != null && TouchUtils.performTouch(service, center)) {
            return NodeActionExecutionResult(true, "已执行中心点触摸")
        }

        // 保留原有逻辑:中心点触摸无法派发时,尝试节点 ACTION_CLICK。
        if (AccessibilityNodeResolver.supportsClick(node)) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return NodeActionExecutionResult(true, "已点击")
            }
        }

        // 向上最多两层找支持 ACTION_CLICK 的祖先节点执行点击。
        val ancestor = AccessibilityNodeResolver.findClickableAncestor(node, MAX_ANCESTOR_LEVELS)
        if (ancestor != null) {
            try {
                if (ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return NodeActionExecutionResult(true, "已点击父布局")
                }
            } finally {
                ancestor.recycle()
            }
        }

        AccessibilityActionExecutor.dumpFailureTree(node)
        val cn = node.className?.toString().orEmpty()
        return NodeActionExecutionResult(false, "节点操作发送失败(class=$cn)")
    }

    private const val MAX_ANCESTOR_LEVELS = 2
}

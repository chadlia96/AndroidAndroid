package com.android.androidAndroid.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.accessibility.monitor.NodeOperateHelper
import com.android.androidAndroid.accessibility.TouchUtils

object ClickExecutor {

    // 点击执行入口:优先执行节点 ACTION_CLICK,节点不支持时向上最多两层找可点击祖先,
    // 仍失败则按节点中心坐标执行一次触摸点击。
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
        // 1. 节点自身支持 ACTION_CLICK 时直接执行。
        if (supportsActionClick(node)) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return NodeActionExecutionResult(true, "已点击")
            }
        }

        // 2. 向上最多两层找支持 ACTION_CLICK 的祖先节点执行点击。
        val ancestor = findClickableAncestor(node)
        if (ancestor != null) {
            try {
                if (ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val cn = ancestor.className?.toString().orEmpty()
                    return NodeActionExecutionResult(true, "已点击")
                }
            } finally {
                ancestor.recycle()
            }
        }

        // 3. 按节点中心坐标执行一次触摸点击。
        val service = NERV.getRunningService()
        val center = service?.let { TouchUtils.centerOf(node) }
        if (service != null && center != null && TouchUtils.performTouch(service, center)) {
            return NodeActionExecutionResult(true, "已执行触摸")
        }

        AccessibilityActionExecutor.dumpFailureTree(node)
        val cn = node.className?.toString().orEmpty()
        return NodeActionExecutionResult(false, "节点操作发送失败(class=$cn)")
    }

    private fun supportsActionClick(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    // 向上最多查找两层,返回最近的支持 ACTION_CLICK 的祖先节点,由调用方负责 recycle。
    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        var level = 1
        while (parent != null && level <= MAX_ANCESTOR_LEVELS) {
            if (supportsActionClick(parent)) return parent
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
            level++
        }
        return null
    }

    private const val MAX_ANCESTOR_LEVELS = 2
}
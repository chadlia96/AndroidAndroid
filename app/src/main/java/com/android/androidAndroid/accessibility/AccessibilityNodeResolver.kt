package com.android.androidAndroid.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView

object AccessibilityNodeResolver {

    fun supportsClick(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    // 仅当直接父节点可点击,且其他直接子节点均不可点击时,将父节点视为当前节点的点击目标。
    // 返回的节点由调用方负责 recycle。
    fun findConstrainedClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val parent = node.parent ?: return null
        if (!supportsClick(parent)) {
            parent.recycle()
            return null
        }

        for (index in 0 until parent.childCount) {
            val child = parent.getChild(index) ?: continue
            try {
                if (child != node && supportsClick(child)) {
                    parent.recycle()
                    return null
                }
            } finally {
                child.recycle()
            }
        }
        return parent
    }

    fun isClickableForSummary(node: AccessibilityNodeInfo): Boolean {
        if (supportsClick(node)) return true
        val parent = findConstrainedClickableParent(node) ?: return false
        parent.recycle()
        return true
    }

    // 返回最近的可点击祖先节点,由调用方负责 recycle。
    fun findClickableAncestor(
        node: AccessibilityNodeInfo,
        maxLevels: Int
    ): AccessibilityNodeInfo? {
        var parent = node.parent
        var level = 1
        while (parent != null && level <= maxLevels) {
            if (supportsClick(parent)) return parent
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
            level++
        }
        return null
    }
}

package com.android.androidAndroid.accessibility

import android.graphics.Rect
import com.android.androidAndroid.logging.AppLogger as Log
import android.view.accessibility.AccessibilityNodeInfo
import com.android.androidAndroid.accessibility.model.ButtonScreenNode
import com.android.androidAndroid.accessibility.model.CheckBoxScreenNode
import com.android.androidAndroid.accessibility.model.ContainerScreenNode
import com.android.androidAndroid.accessibility.model.EditTextScreenNode
import com.android.androidAndroid.accessibility.model.ImageScreenNode
import com.android.androidAndroid.accessibility.model.RadioButtonScreenNode
import com.android.androidAndroid.accessibility.model.RecyclerScreenNode
import com.android.androidAndroid.accessibility.model.ScreenBounds
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.accessibility.model.SeekBarScreenNode
import com.android.androidAndroid.accessibility.model.SwitchScreenNode
import com.android.androidAndroid.accessibility.model.TextScreenNode
import com.android.androidAndroid.accessibility.model.UnknownScreenNode
import com.android.androidAndroid.accessibility.model.WebScreenNode

object AccessibilityNodeMapper {

    // 节点处理结果直接决定当前节点是否入列,以及是否继续查看子树。
    fun traverseNodeTree(node: AccessibilityNodeInfo, out: MutableList<ScreenNode>) {
        if (!seeThroughHisSoul(node, out)) return

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                traverseNodeTree(child, out)
            } finally {
                child.recycle()
            }
        }
    }

    private fun seeThroughHisSoul(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenNode>
    ): Boolean {
        val className = simpleClassName(node)
        val hasChildren = node.childCount > 0

        return when {
            isAtomicControl(node) -> {
                buildSnapshotNode(node)?.let(out::add)
                false
            }

            matchesClass(className, CLASS_IMAGE) -> {
                if (hasDirectContent(node) || hasInteraction(node)) {
                    buildSnapshotNode(node)?.let(out::add)
                }
                false
            }

            matchesClass(className, CLASS_RECYCLER_VIEW) || matchesClass(className, CLASS_WEB_VIEW) -> {
                buildSnapshotNode(node)?.let(out::add)
                hasChildren
            }

            isStructuralContainer(node) -> {
                if (hasDirectContent(node) && hasInteraction(node)) {
                    buildSnapshotNode(node)?.let(out::add)
                }
                hasChildren
            }

            !hasChildren -> {
                if (hasDirectContent(node) || hasInteraction(node)) {
                    buildSnapshotNode(node)?.let(out::add)
                }
                false
            }

            else -> {
                if (hasDirectContent(node) && hasInteraction(node)) {
                    buildSnapshotNode(node)?.let(out::add)
                }
                true
            }
        }
    }

    fun buildSnapshotNode(node: AccessibilityNodeInfo): ScreenNode? {
        val className = simpleClassName(node)
        val text = node.text.easyTrim()
        val description = node.contentDescription.easyTrim()
        val bounds = extractBounds(node)

        return when {
            matchesClass(className, CLASS_CHECK_BOX) -> CheckBoxScreenNode(
                message = firstNonBlank(text, description) ?: "复选框",
                className = className,
                clickable = node.isClickable,
                checkable = node.isCheckable,
                bounds = bounds
            )

            matchesClass(className, CLASS_SWITCH) -> SwitchScreenNode(
                message = firstNonBlank(text, description) ?: "开关",
                className = className,
                clickable = node.isClickable,
                checkable = node.isCheckable,
                bounds = bounds
            )

            matchesClass(className, CLASS_RADIO_BUTTON) -> RadioButtonScreenNode(
                message = firstNonBlank(text, description) ?: "单选框",
                className = className,
                clickable = node.isClickable,
                checkable = node.isCheckable,
                bounds = bounds
            )

            matchesClass(className, CLASS_EDIT_TEXT) -> EditTextScreenNode(
                message = text.ifBlank { "输入框" },
                extraMessage = collectInputLabels(node),
                className = className,
                clickable = node.isClickable,
                editable = node.isEditable,
                bounds = bounds
            )

            matchesClass(className, CLASS_SEEK_BAR) -> SeekBarScreenNode(
                message = firstNonBlank(text, description) ?: "滑块",
                className = className,
                clickable = node.isClickable,
                bounds = bounds
            )

            matchesClass(className, CLASS_BUTTON) -> buildButtonNode(
                node = node,
                className = className,
                text = text,
                description = description,
                bounds = bounds
            )

            matchesClass(className, CLASS_IMAGE) -> ImageScreenNode(
                message = firstNonBlank(description, text) ?: "图片",
                className = className,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                bounds = bounds
            )

            matchesClass(className, CLASS_RECYCLER_VIEW) -> RecyclerScreenNode(
                message = firstNonBlank(text, description) ?: "列表",
                className = className,
                scrollable = node.isScrollable,
                bounds = bounds
            )

            matchesClass(className, CLASS_WEB_VIEW) -> WebScreenNode(
                message = firstNonBlank(text, description) ?: "网页",
                className = className,
                bounds = bounds
            )

            isStructuralContainer(node) -> ContainerScreenNode(
                message = firstNonBlank(text, description) ?: className,
                className = className,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                bounds = bounds
            )

            node.childCount == 0 -> TextScreenNode(
                message = firstNonBlank(text, description) ?: className,
                className = className,
                clickable = AccessibilityNodeResolver.isClickableForSummary(node),
                bounds = bounds
            )

            hasDirectContent(node) && hasInteraction(node) -> UnknownScreenNode(
                message = firstNonBlank(text, description) ?: className,
                className = className,
                bounds = bounds
            )

            else -> null
        }
    }

    private fun buildButtonNode(
        node: AccessibilityNodeInfo,
        className: String,
        text: String,
        description: String,
        bounds: ScreenBounds
    ): ButtonScreenNode {
        // Button 作为整体节点输出,仅在自身无文本时读取后代标签。
        val childText = if (text.isBlank() && description.isBlank()) {
            findFirstUsefulTextInChildren(node)
        } else {
            null
        }
        val message = firstNonBlank(text, description, childText) ?: "按钮"
        val extraMessage = description.takeIf { text.isNotBlank() && it.isNotBlank() && it != text }.orEmpty()

        return ButtonScreenNode(
            message = message,
            extraMessage = extraMessage,
            className = className,
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            bounds = bounds
        )
    }


    fun buildSnapshotDebugText(nodes: List<ScreenNode>): String {
        val content = nodes
            .take(20)
            .joinToString(separator = "\n") { node ->
                "${node.javaClass.simpleName}:${node.message} [${node.className}] " +
                    node.bounds?.describe().orEmpty()
            }
            .ifBlank { "未提取到可用节点" }

        return "==================开始==================\n$content\n==================结束=================="
    }

    private fun collectInputLabels(node: AccessibilityNodeInfo): String {
        val labels = linkedSetOf(node.hintText.easyTrim(), node.contentDescription.easyTrim())

        // 只收集输入框内部的非交互标签,跳过国家选择器等独立控件及其子树.
        fun collectChildren(parent: AccessibilityNodeInfo, depth: Int) {
            if (depth >= INPUT_LABEL_MAX_DEPTH) return
            for (index in 0 until parent.childCount) {
                val child = parent.getChild(index) ?: continue
                try {
                    if (hasInteraction(child)) continue
                    labels.add(child.hintText.easyTrim())
                    labels.add(child.contentDescription.easyTrim())
                    labels.add(child.text.easyTrim())
                    collectChildren(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        collectChildren(node, 0)
        val text = node.text.easyTrim()
        return labels.filter { it.isNotBlank() && it != text }.joinToString(", ")
    }

    private fun findFirstUsefulTextInChildren(node: AccessibilityNodeInfo): String? {
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val content = firstNonBlank(
                    child.text.easyTrim(),
                    child.contentDescription.easyTrim(),
                    findFirstUsefulTextInChildren(child)
                )
                if (content != null) return content
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun hasDirectContent(node: AccessibilityNodeInfo): Boolean {
        return node.text.easyTrim().isNotBlank() || node.contentDescription.easyTrim().isNotBlank()
    }

    private fun hasInteraction(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isEditable || node.isCheckable || node.isLongClickable
    }

    private fun isAtomicControl(node: AccessibilityNodeInfo): Boolean {
        val className = simpleClassName(node)
        return ATOMIC_CONTROL_CLASS_KEYWORDS.any { keyword ->
            matchesClass(className, keyword)
        }
    }

    private fun isStructuralContainer(node: AccessibilityNodeInfo): Boolean {
        val className = simpleClassName(node)
        return CONTAINER_CLASS_KEYWORDS.any { keyword -> matchesClass(className, keyword) } &&
            !matchesClass(className, CLASS_RECYCLER_VIEW)
    }

    private fun simpleClassName(node: AccessibilityNodeInfo): String {
        return node.className
            ?.toString()
            ?.substringAfterLast('.')
            ?.ifBlank { "Unknown" }
            ?: "Unknown"
    }

    fun CharSequence?.easyTrim(default : String = ""): String = this?.toString()?.trim() ?: default

    private fun extractBounds(node: AccessibilityNodeInfo): ScreenBounds {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return ScreenBounds(
            left = rect.left,
            top = rect.top,
            width = rect.width(),
            height = rect.height()
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun matchesClass(className: String, keyword: String): Boolean {
        return className.contains(keyword, ignoreCase = true)
    }

    // 从原始无障碍节点树直接抓取详细信息的调试 dump,每个节点一行,便于在 logcat 中检索。
    fun dumpNodeTree(startNode: AccessibilityNodeInfo, tag: String) {
        try {
            dumpNodeRecursive(startNode, tag, 0)
        } catch (t: Throwable) {
            Log.w(tag, "节点树 dump 中断: ${t.message.orEmpty()}")
        }
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo, tag: String, depth: Int) {
        if (depth > MAX_DUMP_DEPTH) return
        Log.i(tag, buildDumpLine(node, depth))
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                dumpNodeRecursive(child, tag, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun buildDumpLine(node: AccessibilityNodeInfo, depth: Int): String {
        return try {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val actions = node.actionList.joinToString(separator = ",") { action ->
                val label = action.label?.toString().orEmpty()
                if (label.isNotBlank()) "${action.id}:$label" else action.id.toString()
            }
            "  ".repeat(depth) +
                "class=${node.className} pkg=${node.packageName} id=${node.viewIdResourceName} " +
                "window=${node.windowId} bounds=[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}] " +
                "text=${node.text} desc=${node.contentDescription} hint=${node.hintText} tooltip=${node.tooltipText} " +
                "clickable=${node.isClickable} enabled=${node.isEnabled} longClickable=${node.isLongClickable} " +
                "focusable=${node.isFocusable} focused=${node.isFocused} selected=${node.isSelected} " +
                "checked=${node.isChecked} checkable=${node.isCheckable} scrollable=${node.isScrollable} " +
                "editable=${node.isEditable} visible=${node.isVisibleToUser} a11yFocused=${node.isAccessibilityFocused} " +
                "important=${node.isImportantForAccessibility} childCount=${node.childCount} actions=[$actions]"
        } catch (t: Throwable) {
            "  ".repeat(depth) + "节点信息读取失败: ${t.message.orEmpty()}"
        }
    }

    private const val CLASS_BUTTON = "Button"
    private const val CLASS_IMAGE = "Image"
    private const val CLASS_EDIT_TEXT = "EditText"
    private const val CLASS_RECYCLER_VIEW = "RecyclerView"
    private const val CLASS_CHECK_BOX = "CheckBox"
    private const val CLASS_SWITCH = "Switch"
    private const val CLASS_RADIO_BUTTON = "RadioButton"
    private const val CLASS_SEEK_BAR = "SeekBar"
    private const val CLASS_WEB_VIEW = "WebView"
    private const val MAX_DUMP_DEPTH = 12
    private const val INPUT_LABEL_MAX_DEPTH = 4

    private val ATOMIC_CONTROL_CLASS_KEYWORDS = listOf(
        CLASS_CHECK_BOX,
        CLASS_SWITCH,
        CLASS_RADIO_BUTTON,
        CLASS_EDIT_TEXT,
        CLASS_SEEK_BAR,
        CLASS_BUTTON
    )

    private val CONTAINER_CLASS_KEYWORDS = listOf(
        "Layout",
        "ViewGroup",
        "Container",
        "Frame",
        "Card"
    )
}

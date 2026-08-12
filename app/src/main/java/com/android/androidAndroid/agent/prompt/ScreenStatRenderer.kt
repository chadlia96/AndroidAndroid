package com.android.androidAndroid.agent.prompt

import com.android.androidAndroid.androidAndroidApplication
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

object ScreenStatRenderer {

    fun render(nodes: List<ScreenNode>): String {
        if (nodes.isEmpty()) return "未提取到可用节点"
        return nodes.mapIndexed { index, node -> renderStateLine(index + 1, node) }
            .joinToString("\n")
    }

    private fun renderStateLine(index: Int, node: ScreenNode): String {
        val bounds = node.bounds
        val position = bounds?.let(::renderPosition) ?: "?"
        val size = bounds?.let(::renderSize) ?: "?"
        return listOf(
            index.toString(),
            renderComponent(node),
            renderContent(node),
            position,
            size,
            renderCapabilities(node),
        ).joinToString(" | ")
    }

    private fun renderContent(node: ScreenNode): String {
        val message = normalizeContentPart(node.message)
        val extraMessage = node.extraMessage
            .takeIf { it.isNotBlank() && it != node.message }
            ?.let(::normalizeContentPart)
        val content = when {
            !message.isNullOrBlank() && !extraMessage.isNullOrBlank() -> "$message, $extraMessage"
            !message.isNullOrBlank() -> message
            !extraMessage.isNullOrBlank() -> extraMessage
            else -> return "-"
        }
        return if (content.length <= MAX_CONTENT_LENGTH) {
            content
        } else {
            content.take(MAX_CONTENT_LENGTH - 1) + "…"
        }
    }

    private fun normalizeContentPart(content: String): String? {
        return content.replace('|', '｜').replace(Regex("\\s+"), " ").trim().ifBlank { null }
    }

    private fun renderComponent(node: ScreenNode): String = when (node) {
        is ButtonScreenNode -> "Button"
        is EditTextScreenNode -> "Input"
        is ImageScreenNode -> "Image"
        is RecyclerScreenNode -> "List"
        is CheckBoxScreenNode -> "CheckBox"
        is SwitchScreenNode -> "Switch"
        is RadioButtonScreenNode -> "Radio"
        is SeekBarScreenNode -> "Slider"
        is WebScreenNode -> "Web"
        is ContainerScreenNode -> "Container"
        is TextScreenNode -> "Text"
        is UnknownScreenNode -> "Unknown"
    }

    private fun renderPosition(bounds: ScreenBounds): String {
        val pageWidth = androidAndroidApplication.Companion.pageWidth.takeIf { it > 0 } ?: DEFAULT_PAGE_WIDTH
        val pageHeight = androidAndroidApplication.Companion.pageHeight.takeIf { it > 0 } ?: DEFAULT_PAGE_HEIGHT
        val centerX = bounds.left.toLong() + bounds.width.toLong() / 2
        val centerY = bounds.top.toLong() + bounds.height.toLong() / 2
        val horizontal = when {
            centerX < pageWidth.toLong() / 3 -> "左"
            centerX < pageWidth.toLong() * 2 / 3 -> "中"
            else -> "右"
        }
        val vertical = when {
            centerY < pageHeight.toLong() / 3 -> "上"
            centerY < pageHeight.toLong() * 2 / 3 -> "中"
            else -> "下"
        }
        return if (horizontal == "中" && vertical == "中") "中部" else horizontal + vertical
    }

    private fun renderSize(bounds: ScreenBounds): String {
        if (bounds.width <= 0 || bounds.height <= 0) return "?"
        val pageWidth = androidAndroidApplication.Companion.pageWidth.takeIf { it > 0 } ?: DEFAULT_PAGE_WIDTH
        val pageHeight = androidAndroidApplication.Companion.pageHeight.takeIf { it > 0 } ?: DEFAULT_PAGE_HEIGHT
        val reference = minOf(pageWidth, pageHeight).toLong()
        val maxEdge = maxOf(bounds.width, bounds.height).toLong()
        return when {
            maxEdge * 100 <= reference * 15 -> "小"
            maxEdge * 100 <= reference * 75 -> "中"
            else -> "大"
        }
    }

    private fun renderCapabilities(node: ScreenNode): String {
        val capabilities = when (node) {
            is ButtonScreenNode -> buildCapabilities(node.clickable, node.longClickable)
            is ImageScreenNode -> buildCapabilities(node.clickable, node.longClickable)
            is EditTextScreenNode -> buildCapabilities(node.clickable, editable = node.editable)
            is RecyclerScreenNode -> buildCapabilities(scrollable = node.scrollable)
            is CheckBoxScreenNode -> buildCapabilities(node.clickable, checkable = node.checkable)
            is SwitchScreenNode -> buildCapabilities(node.clickable, checkable = node.checkable)
            is RadioButtonScreenNode -> buildCapabilities(node.clickable, checkable = node.checkable)
            is SeekBarScreenNode -> buildCapabilities(node.clickable)
            is ContainerScreenNode -> buildCapabilities(node.clickable, node.longClickable)
            is TextScreenNode, is WebScreenNode, is UnknownScreenNode -> return "?"
        }
        return capabilities.ifEmpty { "-" }
    }

    private fun buildCapabilities(
        clickable: Boolean = false,
        longClickable: Boolean = false,
        editable: Boolean = false,
        checkable: Boolean = false,
        scrollable: Boolean = false,
    ): String = buildList {
        if (clickable) add("点击")
        if (longClickable) add("长按")
        if (editable) add("输入")
        if (checkable) add("勾选")
        if (scrollable) add("滚动")
    }.joinToString(",")

    private const val DEFAULT_PAGE_WIDTH = 1080
    private const val DEFAULT_PAGE_HEIGHT = 1920
    private const val MAX_CONTENT_LENGTH = 80
}
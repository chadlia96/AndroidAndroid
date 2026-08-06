package com.android.androidAndroid.agent

import com.android.androidAndroid.androidAndroidApplication
import com.android.androidAndroid.accessibility.model.ButtonScreenNode
import com.android.androidAndroid.accessibility.model.CheckBoxScreenNode
import com.android.androidAndroid.accessibility.model.ContainerScreenNode
import com.android.androidAndroid.accessibility.model.EditTextScreenNode
import com.android.androidAndroid.accessibility.model.ImageScreenNode
import com.android.androidAndroid.accessibility.model.RadioButtonScreenNode
import com.android.androidAndroid.accessibility.model.RecyclerScreenNode
import com.android.androidAndroid.accessibility.model.SeekBarScreenNode
import com.android.androidAndroid.accessibility.model.SwitchScreenNode
import com.android.androidAndroid.accessibility.model.TextScreenNode
import com.android.androidAndroid.accessibility.model.UnknownScreenNode
import com.android.androidAndroid.accessibility.model.ScreenBounds
import com.android.androidAndroid.accessibility.model.WebScreenNode
import com.android.androidAndroid.agent.prompt.ScreenStatRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNodeRendererTest {

    @After
    fun resetPageSize() {
        androidAndroidApplication.pageWidth = 1080
        androidAndroidApplication.pageHeight = 1920
    }

    @Test
    fun render_usesNineGridAndSizeBoundaries() {
        val nodes = listOf(
            TextScreenNode("A", bounds = ScreenBounds(359, 639, 1, 1)),
            TextScreenNode("B", bounds = ScreenBounds(360, 640, 1, 1)),
            TextScreenNode("C", bounds = ScreenBounds(720, 1280, 162, 162)),
            TextScreenNode("D", bounds = ScreenBounds(0, 0, 810, 810)),
            TextScreenNode("E", bounds = ScreenBounds(0, 0, 811, 811)),
        )

        val lines = ScreenStatRenderer.render(nodes).lines()

        assertEquals("1 | Text | A | 左上 | 小 | ?", lines[0])
        assertEquals("2 | Text | B | 中部 | 小 | ?", lines[1])
        assertEquals("3 | Text | C | 右下 | 小 | ?", lines[2])
        assertTrue(lines[3].contains(" | 中 | ?"))
        assertTrue(lines[4].contains(" | 大 | ?"))
    }

    @Test
    fun render_normalizesContentAndKeepsSixColumns() {
        val longText = "a".repeat(90)
        val nodes = listOf(
            ButtonScreenNode("  添加\n 用户 | ", "补充\t内容", clickable = true, longClickable = true),
            TextScreenNode("", bounds = ScreenBounds(0, 0, 0, -1)),
            TextScreenNode(longText),
        )

        val lines = ScreenStatRenderer.render(nodes).lines()

        assertEquals("1 | Button | 添加 用户 ｜, 补充 内容 | ? | ? | 点击,长按", lines[0])
        assertEquals("2 | Text | - | 左上 | ? | ?", lines[1])
        assertEquals(80, lines[2].split(" | ")[2].length)
        assertTrue(lines[2].split(" | ")[2].endsWith("…"))
        lines.forEach { assertEquals(6, it.split(" | ").size) }
    }

    @Test
    fun render_mapsComponentsAndCollectedCapabilities() {
        val nodes = listOf(
            ButtonScreenNode("", clickable = false, longClickable = false),
            EditTextScreenNode("", clickable = true, editable = true),
            ImageScreenNode("", clickable = true, longClickable = false),
            RecyclerScreenNode("", scrollable = true),
            CheckBoxScreenNode("", clickable = true, checkable = true),
            SwitchScreenNode("", clickable = false, checkable = true),
            RadioButtonScreenNode("", clickable = false, checkable = false),
            SeekBarScreenNode("", clickable = true),
            WebScreenNode(""),
            ContainerScreenNode("", clickable = false, longClickable = true),
            TextScreenNode(""),
            UnknownScreenNode(""),
        )

        val columns = ScreenStatRenderer.render(nodes).lines().map { it.split(" | ") }

        assertEquals(listOf("Button", "Input", "Image", "List", "CheckBox", "Switch", "Radio", "Slider", "Web", "Container", "Text", "Unknown"), columns.map { it[1] })
        assertEquals(listOf("-", "点击,输入", "点击", "滚动", "点击,勾选", "勾选", "-", "点击", "?", "长按", "?", "?"), columns.map { it[5] })
    }

    @Test
    fun render_handlesEmptyNodesAndInvalidGlobalPageSize() {
        assertEquals("未提取到可用节点", ScreenStatRenderer.render(emptyList()))
        androidAndroidApplication.pageWidth = 0
        androidAndroidApplication.pageHeight = -1

        val result = ScreenStatRenderer.render(
            listOf(TextScreenNode("内容", bounds = ScreenBounds(720, 1280, 900, 900)))
        )

        assertEquals("1 | Text | 内容 | 右下 | 大 | ?", result)
    }
}

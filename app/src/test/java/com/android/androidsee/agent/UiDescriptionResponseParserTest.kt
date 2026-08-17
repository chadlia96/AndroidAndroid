package com.android.androidAndroid.agent.action.describeUi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiDescriptionResponseParserTest {

    @Test
    fun parse_acceptsJsonAndCodeFence() {
        val json = """{"summary":"编辑界面","detailDescription":"中央是图片画布."}"""

        assertEquals("编辑界面", UiDescriptionResponseParser.parse(json)?.summary)
        assertEquals(
            "中央是图片画布.",
            UiDescriptionResponseParser.parse("```json\n$json\n```")?.detailDescription
        )
    }

    @Test
    fun parse_rejectsMalformedOrIncompleteResponse() {
        assertNull(UiDescriptionResponseParser.parse("视觉模型调用失败"))
        assertNull(UiDescriptionResponseParser.parse("""{"summary":"","detailDescription":"详细描述"}"""))
        assertNull(UiDescriptionResponseParser.parse("""{"summary":"概括"}"""))
    }
}

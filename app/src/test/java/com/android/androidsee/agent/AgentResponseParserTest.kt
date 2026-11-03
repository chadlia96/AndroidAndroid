package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentResponseParserTest {

    @Test
    fun parse_reasonWithChineseAndClickAction() {
        val json = """
            {"status": "running", "reason": "当前在照片预览界面, 需要找到设为壁纸的选项, 通常隐藏在更多选项菜单中, 所以点击右上角的更多选项按钮。", "actions": [{"op": "click", "target": 8}]}
        """.trimIndent()

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals("running", result!!.status)
        assertEquals(
            "当前在照片预览界面, 需要找到设为壁纸的选项, 通常隐藏在更多选项菜单中, 所以点击右上角的更多选项按钮。",
            result.reason
        )
        assertEquals(1, result.agentActions.size)
        val action = result.agentActions[0]
        assertTrue(action is AgentAction.Click)
        assertEquals(8, (action as AgentAction.Click).target)
    }

    @Test
    fun parse_runningWithSingleClickAction() {
        val json = """{"status": "running", "reason": "测试点击", "actions": [{"op": "click", "target": 3}]}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals("running", result!!.status)
        assertEquals("测试点击", result.reason)
        assertEquals(1, result.agentActions.size)
        assertEquals(AgentAction.Click(3), result.agentActions[0])
    }

    @Test
    fun parse_successWithEmptyActions() {
        val json = """{"status": "success", "reason": "任务完成", "actions": []}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals("success", result!!.status)
        assertEquals("任务完成", result.reason)
        assertTrue(result.agentActions.isEmpty())
    }

    @Test
    fun parse_inputActionWithText() {
        val json = """{"status": "running", "reason": "输入文本", "actions": [{"op": "input", "target": 2, "text": "hello"}]}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals(1, result!!.agentActions.size)
        assertTrue(result.agentActions[0] is AgentAction.Input)
        val input = result.agentActions[0] as AgentAction.Input
        assertEquals(2, input.target)
        assertEquals("hello", input.text)
    }

    @Test
    fun parse_swipeAction() {
        val json = """{"status": "running", "actions": [{"op": "swipe", "startPoint": "100-200", "endPoint": "300-400"}]}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals(1, result!!.agentActions.size)
        assertTrue(result.agentActions[0] is AgentAction.Swipe)
    }

    @Test
    fun parse_launchAppAndBackActions() {
        val json =
            """{"status": "running", "actions": [{"op": "launch_app"}, {"op": "back"}]}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals(2, result!!.agentActions.size)
        assertEquals(AgentAction.LaunchApp, result.agentActions[0])
        assertEquals(AgentAction.Back, result.agentActions[1])
    }

    @Test
    fun parse_thinkWithVisionAction() {
        val json = """{"status": "running", "actions": [{"op": "think_with_vision"}]}"""

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals(1, result!!.agentActions.size)
        assertEquals(AgentAction.ThinkWithVision, result.agentActions[0])
    }

    @Test
    fun parse_rejectsRepeatedVisionRequestEvenAlongsideOtherActions() {
        val json = """{"status":"running","actions":[{"op":"back"},{"op":"think_with_vision"}]}"""

        assertNull(AgentResponseParser.parse(json, allowThinkWithVision = false))
    }

    @Test
    fun parse_visionRoundAllowsNormalActionsAndSuccess() {
        val action = AgentResponseParser.parse(
            """{"status":"running","actions":[{"op":"back"}]}""",
            allowThinkWithVision = false
        )
        assertEquals(AgentAction.Back, action!!.agentActions.single())
        val success = AgentResponseParser.parse(
            """{"status":"success","actions":[]}""",
            allowThinkWithVision = false
        )
        assertEquals("success", success!!.status)
        assertTrue(success.agentActions.isEmpty())
    }

    @Test
    fun parse_codeFencedJson() {
        val json = """
            ```json
            {"status": "running", "actions": [{"op": "click", "target": 1}]}
            ```
        """.trimIndent()

        val result = AgentResponseParser.parse(json)

        assertNotNull(result)
        assertEquals("running", result!!.status)
        assertEquals(1, result.agentActions.size)
        assertEquals(AgentAction.Click(1), result.agentActions[0])
    }

    @Test
    fun parse_invalidInputReturnsNull() {
        assertNull(AgentResponseParser.parse("不是 JSON"))
        assertNull(AgentResponseParser.parse(""))
    }

    @Test
    fun parse_emptyJsonObjectReturnsAgentResponseWithEmptyStatus() {
        val result = AgentResponseParser.parse("{}")
        assertNotNull(result)
        assertEquals("", result!!.status)
        assertTrue(result.agentActions.isEmpty())
    }
}

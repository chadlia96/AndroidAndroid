package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.ActionExecutionResult
import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {

    @Test
    fun buildFinalPrompt_textRoundCanRequestVision() {
        val prompt = AgentPromptBuilder.buildFinalPrompt(AgentSession(goal = "编辑图片"), "节点摘要")

        assertTrue(prompt.contains("本轮未提供截图."))
        assertTrue(prompt.contains("使用 think_with_vision 请求下一轮携带当前截图"))
        assertFalse(prompt.contains("上一次UI视觉探测结果:"))
    }

    @Test
    fun buildFinalPrompt_visionRoundIncludesTaskContextAndForbidsAnotherRequest() {
        val session = AgentSession(goal = "编辑图片")
        session.actionExecutionResults.add(
            ActionExecutionResult(AgentAction.Back, "", true, "返回操作已发送")
        )
        val prompt = AgentPromptBuilder.buildFinalPrompt(
            session, "当前节点摘要", "相册", "保存完成",
            visionStatus = "本轮已附带当前屏幕截图",
            allowThinkWithVision = false
        )

        assertTrue(prompt.contains("编辑图片"))
        assertTrue(prompt.contains("返回操作已发送"))
        assertTrue(prompt.contains("当前节点摘要"))
        assertTrue(prompt.contains("相册"))
        assertTrue(prompt.contains("保存完成"))
        assertTrue(prompt.contains("本轮已附带当前屏幕截图"))
        assertTrue(prompt.contains("禁止再次输出 think_with_vision"))
        assertFalse(prompt.contains("使用 think_with_vision 请求下一轮携带当前截图"))
    }

    @Test
    fun buildFinalPrompt_preservesCaptureFailureAndTextConclusionInHistory() {
        val session = AgentSession(goal = "编辑图片")
        session.actionExecutionResults.add(
            ActionExecutionResult(AgentAction.ThinkWithVision, "", false, "屏幕截图失败")
        )
        session.actionExecutionResults.add(
            ActionExecutionResult(AgentAction.ThinkWithVision, "", true, "视觉决策结论: 当前为裁剪界面")
        )
        val prompt = AgentPromptBuilder.buildFinalPrompt(session, "节点摘要")

        assertTrue(prompt.contains("屏幕截图失败"))
        assertTrue(prompt.contains("视觉决策结论: 当前为裁剪界面"))
        assertTrue(prompt.contains("本轮未提供截图."))
    }
}

package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.ActionExecutionResult
import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.agent.model.DescribeUiActionExecutionResult
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {

    @Test
    fun buildFinalPrompt_appendsDetailWhenLatestResultIsDescribeUi() {
        val session = AgentSession(goal = "编辑图片")
        session.actionExecutionResults.add(
            DescribeUiActionExecutionResult(
                agentAction = AgentAction.DescribeUi,
                target = "",
                success = true,
                message = "一个图片裁剪编辑界面",
                detailDescription = "中央是图片画布,底部是裁剪工具栏."
            )
        )

        val prompt = AgentPromptBuilder.buildFinalPrompt(session, "节点摘要")

        assertTrue(prompt.contains("一个图片裁剪编辑界面"))
        assertTrue(prompt.contains("上一次UI视觉探测结果:"))
        assertTrue(prompt.contains("中央是图片画布,底部是裁剪工具栏."))
    }

    @Test
    fun buildFinalPrompt_doesNotAppendDetailWhenDescribeUiIsNotLatest() {
        val session = AgentSession(goal = "编辑图片")
        session.actionExecutionResults.add(
            DescribeUiActionExecutionResult(
                agentAction = AgentAction.DescribeUi,
                target = "",
                success = true,
                message = "编辑界面",
                detailDescription = "不应作为当前视觉结果注入."
            )
        )
        session.actionExecutionResults.add(
            ActionExecutionResult(AgentAction.Back, "", true, "返回操作已发送")
        )

        val prompt = AgentPromptBuilder.buildFinalPrompt(session, "节点摘要")

        assertFalse(prompt.contains("上一次UI视觉探测结果:"))
        assertFalse(prompt.contains("不应作为当前视觉结果注入."))
    }

    @Test
    fun buildFinalPrompt_doesNotAppendDetailForOrdinaryFailure() {
        val session = AgentSession(goal = "编辑图片")
        session.actionExecutionResults.add(
            ActionExecutionResult(AgentAction.DescribeUi, "", false, "屏幕截图失败")
        )

        val prompt = AgentPromptBuilder.buildFinalPrompt(session, "节点摘要")

        assertFalse(prompt.contains("上一次UI视觉探测结果:"))
    }
}

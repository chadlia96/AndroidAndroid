package com.android.androidAndroid.agent.prompt

import com.android.androidAndroid.agent.AgentSession
import com.android.androidAndroid.agent.action.launchApp.InstalledApp
import com.android.androidAndroid.agent.model.DescribeUiActionExecutionResult
import com.android.androidAndroid.agent.model.JsonDescription

object AgentPromptBuilder {

    fun buildFinalPrompt(
        session: AgentSession,
        stateDescription: String,
        currentApp: String = "",
        latestToast: String = "",
    ): String {
        val historyText = session.actionExecutionResults
            .takeLast(30)
            .withIndex()
            .joinToString(separator = "\n") { (i,v)->
                val targetText = v.target.ifBlank { '-' }
                val successText = if (v.success) "成功" else "失败"
                buildString {
                    append("$i | ")
                    append("${v.agentAction} | ")
                    append("$targetText | ")
                    append("$successText | ")
                    append(v.message)

                }
            }
            .ifBlank { "- 无" }
        val latestVisionText = (session.actionExecutionResults.lastOrNull()
            as? DescribeUiActionExecutionResult)
            ?.takeIf { it.success }
            ?.detailDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "\n上一次UI视觉探测结果:\n$it\n" }
            .orEmpty()
        val toastText = if(latestToast.isNotBlank()) "\n界面上最近的通知: $latestToast" else ""
        return """
你是一个安卓自动化代理。
通过在Android手机上进行Ui和系统操作,完成用户目标任务

JSON返回格式：
{
  "status": "running 或 success",
  "reason": "解释你进行该操作的原因和思考流程",
  ${JsonDescription()}
}

约束：
1. status=success 时 actions 必须为空数组。
2. status=running 时 actions 只能有1个动作
3. 只能使用上述字段，不要添加其他字段。
4. 当节点摘要不足以理解图片、画布、复杂布局或视觉状态时,使用 describe_ui 获取UI视觉描述.

用户目标：
${session.goal}

已执行历史：
序号 | 操作 | 目标 | 结果 | 说明 
$historyText
$latestVisionText

当前 APP:$currentApp

当前界面摘要：(说明：- 表示无或不适用,? 表示未知或未采集,id 仅用于排序)
id | 组件 | 内容 | 位置 | 大小 | 能力
$stateDescription

$toastText

"""
            .trimIndent()
    }

    fun buildLaunchAppPrompt(goal: String, apps: List<InstalledApp>): String {
        val appList = apps.joinToString(separator = "\n") { app ->
            "- 应用名: ${app.appName1}, 包名: ${app.packageName}"
        }
        return """
你负责为安卓自动化任务选择要启动的应用。

用户目标:
$goal

可启动应用列表:
$appList

只返回以下 JSON,不要解释,不要使用代码块:
{"appName":"候选列表中的应用名","packageName":"候选列表中的包名"}

如果没有合适应用,返回:
{"appName":null,"packageName":null}
""".trimIndent()
    }

    const val DESCRIBE_UI_PROMPT = """
你负责观察安卓当前屏幕,为自动化代理提供可靠的UI视觉信息.

请只返回以下 JSON,不要解释,不要使用代码块:
{"summary":"一句极简UI概括","detailDescription":"详细UI视觉描述"}

要求:
1. summary 只说明界面性质和主要状态,例如“一个图片裁剪编辑界面”.
2. detailDescription 按顺序描述界面类型、布局层级、可见文字、控件及状态、图片或画布内容、弹窗与遮挡、明显的可操作线索.
3. 区分已选中、禁用、加载中和被遮挡状态.
4. 不臆测画面外、遮挡后或无法辨认的内容.
"""
}

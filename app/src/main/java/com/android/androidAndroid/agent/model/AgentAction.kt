/*
LLM 修改指南: 新增或修改 Action 时,同步更新:
1. 本文件的 AgentAction 子类和 JsonDescription().
2. AgentResponseParser.kt 的 RawAction、JSON 字段读取和 toAction().
3. AgentLoop.kt 的目标节点与结果 target 分支.
4. AccessibilityActionExecutor.kt 的执行分支,以及相关解析和执行测试.
*/

package com.android.androidAndroid.agent.model


sealed class AgentAction(
    val op: String
) {
    data class Click(
        val target: Int,
    ) : AgentAction("click") {
        override fun toString(): String = "点击节点, 节点ID: $target"
    }

    data class Input(
        val target: Int,
        val text: String
    ) : AgentAction("input") {
        override fun toString(): String = "在节点ID $target 输入文本: \"$text\""
    }

    data class Swipe(
        val startPoint: Position,
        val endPoint: Position,
    ) : AgentAction("swipe") {
        override fun toString(): String = "从坐标 $startPoint 滑动到坐标 $endPoint"
    }

    data object LaunchApp : AgentAction("launch_app") {
        override fun toString(): String = "选择并启动应用"
    }

    data object Back : AgentAction("back") {
        override fun toString(): String = "返回上一页"
    }

    data object DescribeUi : AgentAction("describe_ui") {
        override fun toString(): String = "描述当前界面"
    }
}

fun JsonDescription(): String{
    return """
"actions": [
  {
    "op": "click | input | swipe | launch_app | back | describe_ui",
    "target": 2, //可选,操作对象的id
    "text": "Sample Text", //可选,仅 input 使用
    "startPoint": "30-50", //可选,仅 swipe 使用,滑动起点坐标
    "endPoint": "30-20", //可选,仅 swipe 使用,滑动终点坐标
  }
],
""".trimIndent()
}

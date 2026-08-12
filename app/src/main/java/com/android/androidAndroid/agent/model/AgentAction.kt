/*
## 重要提示
添加 Action 子类后,请根据动作来源和用途同步检查:
1. JsonDescription() 中的 op 列表和动作字段说明.
2. AgentResponse.kt 中 RawAction 的字段、JSON 字段读取和 toAction() 转换分支.
3. AgentLoop.kt 中 executeAction() 的目标节点解析、ActionResult.target 生成和实际执行逻辑.
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
}

fun JsonDescription(): String{
    return """
"actions": [
  {
    "op": "click | input | swipe | launch_app | back",
    "target": 2, //可选,操作对象的id
    "text": "Sample Text", //可选,仅 input 使用
    "startPoint": "30-50", //可选,仅 swipe 使用,滑动起点坐标
    "endPoint": "30-20", //可选,仅 swipe 使用,滑动终点坐标
  }
],
""".trimIndent()
}

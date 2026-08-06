# Agent Loop 设计文档

本文记录本项目 Agent Loop 当前已经讨论确认的设计。未确认内容不提前补全,必须标记为 `待定`。

## 1. 当前阶段目标

当前阶段目标是验证 Android 无障碍场景下的 Agent Loop 主链路:

- 接收用户输入.
- 读取当前界面信息.
- 构建模型上下文.
- 让模型输出动作协议.
- 记录动作执行结果.
- 将运行状态反馈给用户.

当前阶段优先验证采集,Prompt,动作协议和流程沉淀能力,不追求完整自动化执行。

## 2. 用户输入

用户侧只暴露输入概念,不暴露 Task 概念。

用户输入统一封装为 `UserInput`。第一版至少包含:

```kotlin
data class UserInput(
    val type: UserInputType,
    val message: String
)

enum class UserInputType {
    TEXT
}
```

`UserInput.message` 保存用户输入文本。`UserInputType` 用于给后续扩展预留边界,例如图片输入,语音输入,系统反馈或取消指令。

用户输入可能是发起目标,补充约束,运行反馈,取消请求,普通询问或其他内容。第一版由 `AGENT_LOOP` 自行判断边界。

## 3. Session

第一版引入 `AgentSession` 概念。

当前只支持单 session:

- 固定 session id 为 `AGENT_LOOP`.
- `AGENT_LOOP` 本身就是当前唯一 session.
- 每次重新启动 Agent Loop 时,重新创建这个单 session.
- 暂不支持多 session 并存,恢复,切换或后台 session.

第一版 `AgentSession` 不再额外封装二层 `Goal`。目标文本直接作为 session 字段保存,避免过早抽象。

建议第一版结构:

```kotlin
data class AgentSession(
    val sessionId: String = "AGENT_LOOP",
    val goal: String,
    var status: AgentSessionStatus,
    val observations: MutableList<AgentObservation>,
    val modelOutputs: MutableList<AgentModelOutput>,
    val toolResults: MutableList<AgentToolResult>,
    var turnCount: Int,
    val maxTurns: Int,
    val metadata: MutableMap<String, String>
)
```

相关数据类第一版只要求表达运行记录,字段细节待实现时确认。

当前已经确认:

- 新增 `AgentSession`.
- 完全删除 `LLMTask`,不再保留 `LLMTaskStatus`.
- `AgentSession` 不包含二层 `Goal`.
- `sessionId` 固定为 `"AGENT_LOOP"`.
- `goal` 直接保存用户首次输入文本.
- `status` 表达 session 当前状态.
- `observations` 记录界面采集结果.
- `modelOutputs` 记录模型原始输出和解析后的动作.
- `actionResults` 记录动作执行结果.
- `turnCount` 和 `maxTurns` 替代原任务步数限制.
- `metadata` 保存临时运行信息和错误信息.

## 4. Agent Loop 主流程

Agent Loop 参考 `AGENT_ENGINEERING_GUIDE.md` 中“推荐 Agent loop 伪代码”的结构。

第一版流程:

```text
user_input = receive_user_input()
session = init_session(user_input)

while session.active:
    observation = observe_current_screen()
    session.add_observation(observation)

    context = build_context(session)
    model_output = model_runtime.generate(context)
    session.add_model_output(model_output)

    if model_output.has_final_answer():
        session.stop("success")
        break

    for tool_call in model_output.tool_calls:
        validation = validate(tool_call)
        if validation.failed:
            session.add_tool_result(tool_call, validation.error)
            continue

        result = execute_or_record(tool_call)
        session.add_tool_result(tool_call, result)

    if budget_exceeded(session):
        session.stop("budget_exceeded")
```

当前阶段的 `tool_call` 可以先对应 Android 无障碍动作协议,例如 `click`,`swipe`,`input`,`launch_app`,`back`。

当前已经确认:

- `AgentLoop` 保持 `object` 单例.
- `AgentLoop` 对外接收 `UserInput`.
- 首次收到 `UserInput` 时懒启动 `AgentSession`.
- 收到输入后进入 loop.
- 生成新 `AgentSession` 时清空悬浮按钮旧点击事件.
- 每一轮 loop 开始前,都必须点击一次悬浮按钮才继续执行.
- 如果悬浮按钮不可用,当前 session 失败并记录错误.
- `SecondFragment` 的 AGENT 模式只负责封装 `UserInput` 并发送给 `AgentLoop`.
- 纯对话模式仍然直接调用 `LLMManager`.
- 当前运行反馈暂只写 `Log`.
- 保留现有采集界面,构建 Prompt,调用 LLM,解析 `AgentCommand`,降级手动执行日志的核心行为.
- 保留 `maxTurns` 和失败次数限制等保护.

## 5. 执行策略

当前阶段允许将动作执行降级为手动验证:

- 模型仍然输出动作协议.
- Agent Loop 记录模型给出的动作.
- 自动执行可以暂时关闭.
- 用户根据记录内容手动操作,用于验证采集,Prompt,动作协议和流程沉淀能力.

自动执行策略,权限拦截和执行结果校验为 `待定`。

## 6. Action 与 ActionResult

当前已经确认:

- `data/Action` 暂时代替传统 Agent tools/tool_call.
- 新增独立 `ActionResult`.
- `ActionResult` 用于记录 `Action` 或 `AgentCommand` 执行结果.
- 不把执行结果塞回 `Action`.
- `wait` 语义第一版只写 TODO,暂不实现实际等待协议.

## 7. 待定

- 用户输入到 session goal 的判定规则.
- 取消,暂停,继续和用户反馈的具体协议.
- 自动执行动作的开启条件.
- 权限检查和风险动作确认机制.
- 最终结果回显格式.

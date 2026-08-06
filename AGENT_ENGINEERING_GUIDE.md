# Agent 工程化设计指南

本文面向 Harness Engineer 与未来参与编码 Agent 建设的人。它既回答“Agent 是什么”，也把 Claude Code、Codex 这类成熟编码 Agent 暴露出的工程结构抽象成可落地的设计规范。

## 1. 基本定义

### 1.1 什么是 Agent

Agent 不是一个单独的大模型，而是一个围绕大模型构建的任务执行系统。

一个工程化 Agent 至少包含：

- **目标输入**：用户给出的任务、上下文、约束、验收标准。
- **推理核心**：LLM 负责理解目标、分解任务、选择下一步动作。
- **工具层**：文件读写、Shell、搜索、浏览器、数据库、Git、CI、MCP 服务等。
- **状态层**：对话历史、任务计划、已读文件、工具结果、错误、成本、权限状态。
- **控制层**：权限、预算、轮次、沙箱、审批、停止条件、审计日志。
- **反馈层**：把工具执行结果回传给模型，让模型继续决策。

因此，Agent 可以理解为：**LLM 加上工具、状态、控制、反馈闭环之后形成的可执行系统**。

### 1.2 Agent 是否是 LLM 的工程化应用

是，但不只是“调用 LLM API”。

普通 LLM 应用通常是：

```text
用户输入 -> 模型输出 -> 展示结果
```

Agent 应用通常是：

```text
用户目标 -> 模型决策 -> 工具执行 -> 结果回灌 -> 再决策 -> 验证 -> 交付
```

区别在于 Agent 需要承担真实工程副作用，例如修改文件、运行测试、提交代码、调用外部系统。它必须具备边界、权限、回滚、审计和验收机制。

### 1.3 Agent loop 是什么

Agent loop 是 Agent 的运行主循环。成熟 Agent 的核心循环基本一致：

1. 接收任务、系统提示、项目规则、工具定义和历史上下文。
2. 模型评估当前状态，输出文本、计划或工具调用请求。
3. Harness 执行工具调用，收集标准化结果。
4. 工具结果作为新上下文回灌给模型。
5. 重复决策与执行，直到模型不再请求工具、达到预算上限、触发权限拦截、出现不可恢复错误，或用户中止。
6. 输出最终结果、变更摘要、验证结果、剩余风险。

Claude Agent SDK 官方文档把这个循环描述为：模型评估提示，调用工具行动，收到结果，然后重复直到任务完成。Codex 也公开讨论过类似的编码 Agent loop：模型在本地或云端环境中观察代码、执行命令、修改文件、再验证。

## 2. 成熟编码 Agent 的真实共性

### 2.1 Claude Code 暴露出的结构

Claude Code/Agent SDK 文档中可以看到几个明确的工程层：

- **消息生命周期**：SystemMessage、AssistantMessage、UserMessage、ResultMessage 等，用于描述会话初始化、模型输出、工具结果和最终结果。
- **工具系统**：读文件、编辑文件、写文件、搜索、Shell、Web、编排工具、MCP 工具。
- **权限模式**：允许工具、禁止工具、需要用户审批、只读计划模式、隔离环境中的绕过权限模式。
- **上下文窗口治理**：系统提示、项目规则、工具定义、历史消息、工具输出都会占用上下文；长任务需要压缩、摘要和持久规则。
- **Hook**：在工具调用前后、会话开始/结束、压缩前等时机插入控制逻辑。
- **Subagent**：把特定任务委派给专门 Agent，主 Agent 只接收结果。
- **MCP**：通过标准协议接入外部工具与系统。

这些不是“提示词技巧”，而是完整 Agent Harness 的组成部分。

### 2.2 Codex 暴露出的结构

Codex 的公开资料强调几个相同方向：

- **本地或云端隔离执行环境**：Agent 可以读写代码、执行命令，但必须受沙箱和权限约束。
- **AGENTS.md 项目规则层**：项目约束、编码风格、验证命令、禁止事项要成为 Agent 的稳定上下文，而不是每次临时口头说明。
- **代码变更闭环**：理解任务、读代码、修改、运行测试、总结结果。
- **人机协作边界**：Agent 负责执行和验证，人负责目标设定、审批高风险动作、评估业务取舍。

因此，Claude 与 Codex 的共同结论是：**好的编码 Agent 不是更长的 Prompt，而是可控的工程执行系统**。

## 3. Agent 的主要架构模块

### 3.1 Harness

Harness 是 Agent 的运行框架。它不负责“聪明”，但负责让聪明变得可靠。

Harness 的职责：

- 组装系统提示、用户任务、项目规则、工具定义。
- 管理 Agent loop。
- 调度工具调用。
- 记录上下文、成本、轮次、状态。
- 执行权限判断。
- 处理工具错误、超时、重试、中止。
- 生成最终报告。

Harness Engineer 的核心工作是：**让模型的每一步行动都可观察、可限制、可复现、可验收**。

### 3.2 Model Runtime

Model Runtime 负责调用模型，并将模型输出解析为可执行意图。

关键设计点：

- 支持流式输出与非流式输出。
- 区分自然语言输出与工具调用。
- 对模型输出做 schema 校验。
- 保留原始模型响应用于审计。
- 支持不同模型、不同 reasoning effort、不同成本策略。
- 支持失败重试，但不能无限重试。

### 3.3 Tool Registry

Tool Registry 是工具目录。

每个工具必须声明：

- 名称。
- 功能描述。
- 输入 schema。
- 输出 schema。
- 是否只读。
- 是否有副作用。
- 是否需要审批。
- 超时时间。
- 可执行范围。
- 错误类型。

工具设计原则：

- 工具能力要窄，不要把多个动作塞进一个万能工具。
- 工具输出要结构化，避免把大量噪声塞回上下文。
- 只读工具可并行；写操作、Shell、外部系统调用默认串行。
- 高风险工具必须可拦截、可审计。

### 3.4 Permission Manager

Permission Manager 决定工具调用是否能执行。

建议把权限分成四类：

- **Read**：读文件、搜索、查看状态。默认允许。
- **Write**：编辑文件、创建文件。需要受工作区限制。
- **Execute**：运行命令、测试、构建。按命令前缀或白名单控制。
- **External**：网络、数据库、生产系统、GitHub 写操作。默认需要审批。

权限结果要作为工具结果返回给模型，让模型知道为什么某个动作被拒绝，而不是静默失败。

### 3.5 Context Manager

Context Manager 负责控制模型看到什么。

上下文来源：

- 系统提示。
- 用户任务。
- 项目规则文件，例如 AGENTS.md。
- 已读文件内容。
- 工具定义。
- 工具执行结果。
- 计划、决策、错误和测试输出。

核心原则：

- 持久规则写入项目规则文件，不依赖长对话记忆。
- 大文件按需读取，不一次性塞入全部仓库。
- 工具输出要截断、摘要、保留关键错误。
- 压缩上下文时必须保留目标、约束、文件路径、已修改内容、测试结果和未解决风险。

### 3.6 Planner

Planner 负责把目标拆成可执行步骤。

计划不一定要很长，但必须包含：

- 当前目标。
- 约束。
- 需要读取或修改的区域。
- 最小变更路径。
- 验收标准。
- 风险点。

计划应该随着工具结果更新。计划不是仪式，而是防止 Agent 在长循环中漂移。

### 3.7 Executor

Executor 执行工具调用。

执行层必须处理：

- 参数校验。
- 权限检查。
- 沙箱限制。
- 并发控制。
- 超时。
- stdout/stderr 截断。
- 退出码。
- 异常标准化。
- 结果回灌。

Executor 不应该把工具失败解释成成功。失败必须清晰暴露给模型和用户。

### 3.8 Memory

Memory 分为短期记忆和长期记忆。

- **短期记忆**：当前任务上下文、已读文件、工具结果、计划状态。
- **长期记忆**：项目规则、用户偏好、团队规范、常用命令、架构说明。

长期记忆必须可编辑、可审计、可覆盖。不要把不可见记忆作为工程事实来源。

### 3.9 Verifier

Verifier 负责判断任务是否真的完成。

常见验证方式：

- 编译。
- 单元测试。
- 集成测试。
- lint。
- 类型检查。
- UI 截图。
- 手动验收清单。
- diff 检查。

Verifier 的结论要区分：

- 已验证通过。
- 验证失败。
- 未运行，说明原因。
- 部分验证，列出剩余风险。

### 3.10 Observer

Observer 提供可观察性。

至少记录：

- 用户目标。
- 每轮模型决策摘要。
- 工具调用参数。
- 权限决策。
- 工具结果。
- 文件变更。
- token/成本。
- 错误与重试。
- 最终验证结果。

生产级 Agent 需要日志、trace、metrics、审计事件和可回放记录。

## 4. 推荐 Agent loop 伪代码

```text
session = init_session(user_goal, project_rules, tool_registry, budgets)

while session.active:
    context = context_manager.build(session)
    model_output = model_runtime.generate(context)

    observer.record_model_output(model_output)

    if model_output.has_final_answer():
        break

    for tool_call in model_output.tool_calls:
        validation = tool_registry.validate(tool_call)
        if validation.failed:
            session.add_tool_result(tool_call, validation.error)
            continue

        permission = permission_manager.check(tool_call, session)
        if permission.denied:
            session.add_tool_result(tool_call, permission.reason)
            continue

        result = executor.run(tool_call)
        observer.record_tool_result(tool_call, result)
        session.add_tool_result(tool_call, result)

    if budgets.exceeded(session):
        session.stop("budget_exceeded")

    if user_cancelled():
        session.stop("user_cancelled")

final_report = verifier_and_reporter.finish(session)
return final_report
```

## 5. Harness Engineer 编程指导

### 5.1 设计优先级

按以下顺序设计 Agent Harness：

1. 正确性。
2. 权限安全。
3. 可观察性。
4. 可恢复性。
5. 成本与速度。
6. 用户体验。

不要为了“自动化程度”牺牲可控性。

### 5.2 工具接口规范

每个工具建议使用统一结构：

```text
ToolDefinition:
  name
  description
  input_schema
  output_schema
  read_only
  side_effect_level
  permission_policy
  timeout_ms
  max_output_bytes
  handler
```

工具输出建议使用：

```text
ToolResult:
  ok
  data
  error_code
  error_message
  stdout
  stderr
  exit_code
  truncated
  metadata
```

不要只返回一段字符串。结构化输出可以降低模型误判。

### 5.3 权限策略

默认策略：

- 读操作默认允许。
- 写工作区内文件允许，但必须记录 diff。
- 运行测试、编译等常见命令可以白名单。
- 删除、移动、大规模格式化、网络访问、Git push、发布、生产数据库写入必须审批。
- 权限拒绝要清楚告诉模型原因。

高风险动作要支持：

- dry-run。
- diff 预览。
- 用户确认。
- 回滚点。
- 审计日志。

### 5.4 上下文策略

Harness 不能把“整个世界”塞给模型。

推荐策略：

- 先搜索，再读取。
- 先读相关文件，再读依赖文件。
- 大文件分段读取。
- 长命令输出保留错误行、摘要和退出码。
- 计划、验收标准、已修改文件必须常驻上下文。
- 历史压缩后要保留关键决策。

### 5.5 子 Agent 策略

Subagent 适合：

- 独立代码审查。
- 安全审查。
- 文档总结。
- 大范围搜索。
- 互不共享写状态的探索任务。

Subagent 不适合：

- 多个 Agent 同时改同一批文件。
- 权限边界不清的生产操作。
- 需要强一致上下文的复杂实现。

主 Agent 必须负责最终整合，不能把责任完全转交给子 Agent。

### 5.6 Hook 策略

Hook 是 Harness 的控制点。

常见 Hook：

- SessionStart：注入项目规则、初始化日志。
- PreToolUse：权限检查、参数审计、危险命令拦截。
- PostToolUse：结果截断、错误归类、指标记录。
- PreCompact：保存完整上下文、提取关键状态。
- Stop：最终报告、资源清理、通知用户。

Hook 必须尽量确定、快速、可测试。Hook 失败时要有明确降级策略。

### 5.7 错误处理策略

错误要分层：

- 用户输入错误：目标不清、缺少权限、缺少环境。
- 模型错误：工具参数不合法、误解任务、重复无效尝试。
- 工具错误：命令失败、文件不存在、超时。
- 环境错误：依赖缺失、网络不可达、沙箱拒绝。
- 业务错误：测试失败、验收不通过。

每类错误都应有明确处理：

- 可重试：限制次数，改变策略后重试。
- 不可重试：停止并报告。
- 需要用户输入：提出具体问题。
- 需要审批：展示风险和命令。

### 5.8 验收输出规范

最终回复必须包含：

- 做了什么。
- 改了哪些文件。
- 如何验证。
- 验证结果。
- 未完成或未验证的风险。

不要说“应该可以”。要说“已运行 X，通过/失败/未运行，原因是 Y”。

## 6. Agent 设计反模式

- **只靠提示词**：没有工具 schema、权限和验证，无法工程化。
- **万能工具**：一个工具做所有事，权限和审计都会失控。
- **无边界 Shell**：让模型随意执行命令，会放大误操作风险。
- **上下文堆砌**：读取过多文件导致模型注意力下降和成本上升。
- **无验收闭环**：只修改不测试，用户无法信任结果。
- **隐藏记忆**：用不可见状态影响决策，调试和审计困难。
- **并发写操作**：多个 Agent 或工具同时改同一文件，容易产生冲突。
- **失败静默化**：工具失败却继续包装成成功。

## 7. 面向本项目的实践建议

本仓库是 Android Accessibility 项目。后续 Harness 或 Agent 在此类项目中工作时，应遵守：

- 优先读取根目录 `AGENTS.md`。
- 主要工作范围限制在 `app/src/main/`、`src/main/java/`、`src/main/kotlin/`、`src/main/res/` 和 Manifest/config。
- 不主动分析 `build/`、`.gradle/`、生成文件。
- 修改前先复述目标、约束、文件、最小计划、验收标准。
- 中文注释和 `strings.xml` 文案必须保持中文。
- 命令行读写文件时使用 UTF-8。
- 以最小变更完成任务。
- Android 验证优先使用编译、相关模块测试和人工可检查 diff。

## 8. 可复用架构蓝图

```text
User / Engineer
      |
      v
Task Intake
  - goal
  - constraints
  - acceptance criteria
      |
      v
Agent Harness
  - loop controller
  - planner
  - context manager
  - permission manager
  - observer
      |
      v
Model Runtime <----> Memory / Project Rules
      |
      v
Tool Router
  - file tools
  - search tools
  - shell tools
  - browser tools
  - git tools
  - MCP tools
      |
      v
Sandbox / External Systems
      |
      v
Verifier
      |
      v
Final Report
```

## 9. 最小可行 Harness 清单

如果从零实现一个编码 Agent Harness，第一版至少需要：

- Agent loop。
- 模型调用封装。
- 工具注册表。
- 文件读取工具。
- 文件编辑工具。
- 搜索工具。
- 命令执行工具。
- 权限管理。
- 工作区沙箱。
- 上下文构建器。
- 工具结果标准化。
- 计划状态。
- 验证命令执行。
- 最终报告生成。
- 日志与审计。

第二阶段再加入：

- MCP。
- Hook。
- Subagent。
- 自动上下文压缩。
- 成本预算。
- 多模型路由。
- Checkpoint/rollback。
- Trace 与指标平台。

## 10. 设计原则总结

Agent 的本质不是“模型替人写代码”，而是“模型在受控工程系统中持续观察、决策、行动和验证”。

一个可靠 Agent 必须同时满足：

- 能理解目标。
- 能选择工具。
- 能执行动作。
- 能看见结果。
- 能修正方向。
- 能接受约束。
- 能证明完成。
- 能在风险出现时停下来。

Harness Engineer 的责任是把这些能力做成稳定、可审计、可扩展的基础设施。

## 参考资料

- Claude Code Agent loop 文档：https://code.claude.com/docs/en/agent-sdk/agent-loop
- Claude Code Hooks 文档：https://code.claude.com/docs/en/agent-sdk/hooks
- Claude Code MCP 文档：https://code.claude.com/docs/en/agent-sdk/mcp
- Claude Code Best Practices：https://code.claude.com/docs/en/best-practices
- OpenAI Codex Agent loop 文章：https://openai.com/index/unrolling-the-codex-agent-loop/
- OpenAI Agents 指南：https://developers.openai.com/api/docs/guides/agents

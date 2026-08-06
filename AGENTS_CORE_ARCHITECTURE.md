# AGENTS 核心架构设计文档

本文是一份面向 Harness Engineer 的中文架构文档，用于说明成熟编码 Agent 的核心结构、主要功能和可复用实现方式。它不是 `AGENTS.md` 文件格式说明，而是从公开源码与公开文档中抽象出的编码 Agent / Agent Harness 工程设计指南。

## 1. 资料边界

主要源码参考来自 `ChinaSiro/claude-code-sourcemap`：

- 仓库地址：https://github.com/ChinaSiro/claude-code-sourcemap
- 仓库 README 声明：该项目是非官方整理版，基于公开 npm 包 `@anthropic-ai/claude-code` 中的 source map 还原源码，用于研究；不代表 Anthropic 官方原始内部仓库结构。
- 还原版本：`2.1.88`。
- README 中列出的还原规模：4756 个文件，其中包含 1884 个 `.ts` / `.tsx` 源文件。

因此，本文把它作为“公开可阅读源码样本”来研究架构，不把它描述为官方源码仓库，也不复制大段源码。本文引用接口名、目录名、职责和设计思想，用于后续工程实现。

交叉参考：

- Claude Code Agent loop 文档：https://code.claude.com/docs/en/agent-sdk/agent-loop
- Claude Code Hooks 文档：https://code.claude.com/docs/en/agent-sdk/hooks
- Claude Code MCP 文档：https://code.claude.com/docs/en/agent-sdk/mcp
- OpenAI Codex agent loop 文章：https://openai.com/index/unrolling-the-codex-agent-loop/

## 2. Agent / AGENTS / Agent loop 的工程定义

### 2.1 Agent

Agent 不是单独的大模型，而是一个能围绕目标持续观察、决策、行动和验证的工程系统。

一个编码 Agent 至少包含：

- 模型运行时：负责理解目标、生成下一步动作。
- 工具系统：负责读写文件、搜索、执行命令、访问 Web、调用 MCP、创建任务。
- 上下文系统：负责组织系统提示、项目规则、消息历史、工具结果、压缩摘要。
- 权限系统：负责拦截高风险工具调用。
- 执行系统：负责真正调用工具并把结果回灌给模型。
- 观察系统：负责记录工具调用、错误、成本、状态和最终验证。

### 2.2 AGENTS

本文中的 AGENTS 指编码 Agent 体系，不只指 `AGENTS.md`。

`AGENTS.md` 是项目规则层，负责把人类团队的工程约束稳定注入 Agent 上下文，例如代码风格、目录范围、验证命令、禁止事项、语言规则。成熟 Agent 不应依赖临时对话记忆保存这些约束。

### 2.3 Agent loop

Agent loop 是 Agent 的主循环：

```text
用户目标
  -> 构建上下文
  -> 调用模型
  -> 模型输出文本或工具调用
  -> 执行工具
  -> 工具结果回灌
  -> 再次调用模型
  -> 直到无工具调用、预算耗尽、权限拒绝、出错或用户中止
  -> 输出最终报告
```

Claude Agent SDK 文档也把核心循环描述为：模型评估提示，调用工具行动，收到工具结果，然后重复直到任务完成。Codex 公开文章讨论的本地软件 Agent loop 也遵循类似结构：观察仓库、执行命令、编辑代码、验证结果。

## 3. 从 claude-code-sourcemap 观察到的源码分层

`restored-src/src/` 目录暴露出一个成熟编码 Agent 的典型分层：

| 层级 | 代表目录或文件 | 主要职责 |
| --- | --- | --- |
| 入口层 | `main.tsx`、`cli/`、`entrypoints/` | CLI 启动、参数解析、认证、配置、REPL 或非交互入口 |
| Query loop | `query.ts`、`QueryEngine.ts` | 模型调用主循环、消息流、工具块收集、上下文压缩、错误恢复 |
| 工具抽象 | `Tool.ts` | 定义 Tool 接口、工具上下文、工具结果、权限上下文 |
| 工具注册 | `tools.ts`、`tools/` | 装配 Bash、Read、Edit、Grep、MCP、Agent、Skill、Task 等工具 |
| 命令系统 | `commands/`、`commands.ts` | slash command、commit、review、config 等命令 |
| 服务层 | `services/` | API、MCP、compact、analytics、tool orchestration 等服务 |
| 权限与 Hook | `hooks/`、`types/permissions.ts`、`utils/permissions/` | 工具审批、规则匹配、Pre/Post Tool 控制点 |
| 上下文与消息 | `context/`、`context.ts`、`types/message.ts`、`utils/messages.ts` | 用户上下文、系统上下文、消息规范化、附件 |
| 子 Agent / 协调 | `coordinator/`、`tools/AgentTool/`、`tasks/` | 子 Agent、任务、团队、后台会话、协调模式 |
| 插件与技能 | `plugins/`、`skills/`、`tools/SkillTool/` | 可复用工作流、插件装载、能力扩展 |
| UI / REPL | `components/`、`screens/`、`ink/`、`hooks/` | 终端 UI、输入、提示、diff、状态展示 |
| 配置与遥测 | `utils/config`、`services/analytics`、`cost-tracker.ts` | 设置、功能开关、成本、事件记录 |

这个结构说明成熟 Agent 已经不是“单个 prompt + shell”。它更接近一个带 UI、权限、工具总线、任务系统和运行时控制的开发平台。

## 4. 入口层：启动、配置与运行模式

`main.tsx` 体现了入口层的复杂性。它不是直接调用模型，而是先做启动准备：

- 启动性能打点。
- 并行预取系统配置、密钥、托管设置。
- 初始化 CLI 参数。
- 加载用户上下文与系统上下文。
- 初始化遥测、认证、策略限制、MCP 注册表。
- 装配工具集合。
- 进入 REPL、print、SDK 或其他入口模式。

入口层的设计要点：

- 启动阶段要尽早并行化慢操作，例如认证、配置、远程策略读取。
- 入口层不应混入业务工具逻辑，只负责装配运行环境。
- 交互模式与非交互模式要共享核心 loop，避免两套 Agent 行为。
- 配置、策略和权限要在进入 loop 前准备好，不能让模型自行猜测环境。

## 5. Query loop：Agent 的核心执行循环

`query.ts` 是 Agent loop 的核心样本。它展示了成熟 Agent 在一次用户请求中需要处理的真实问题。

### 5.1 输入状态

Query loop 接收的关键输入包括：

- `messages`：当前消息历史。
- `systemPrompt`：系统提示。
- `userContext`：用户上下文。
- `systemContext`：系统上下文。
- `canUseTool`：工具权限判断函数。
- `toolUseContext`：工具执行上下文。
- `fallbackModel`：降级模型。
- `maxTurns`、`taskBudget`、`maxOutputTokensOverride`：轮次和预算控制。

这些输入说明 loop 不是无状态调用，而是一个带预算、权限、工具、模型和消息历史的状态机。

### 5.2 每轮执行流程

可抽象为：

```text
准备 messagesForQuery
  -> 应用工具结果预算
  -> 应用 snip / microcompact / context collapse / autocompact
  -> 构建完整 system prompt
  -> 判断上下文是否接近阻塞限制
  -> 调用模型流式接口
  -> 收集 assistant message 和 tool_use block
  -> 流式执行可执行工具
  -> 处理 fallback / abort / prompt too long / media error
  -> 执行 post-sampling hook
  -> 如有工具结果，进入下一轮
  -> 如无工具调用，进入 stop hook 和最终返回
```

### 5.3 上下文压缩

源码中可以看到多种上下文治理策略：

- `applyToolResultBudget`：限制工具结果总量，避免大输出拖垮上下文。
- `snipCompactIfNeeded`：裁剪历史。
- `microcompact`：微压缩。
- `contextCollapse`：上下文折叠。
- `autocompact`：自动压缩。
- `reactiveCompact`：遇到 prompt too long 或媒体过大错误后的反应式压缩。

工程结论：

- 长任务必须有上下文预算系统。
- 压缩不是单一策略，而是多层策略组合。
- 工具输出是上下文膨胀的重要来源，必须单独治理。
- 压缩后要保留目标、约束、已修改文件、测试结果和关键决策。

### 5.4 流式模型与流式工具执行

`query.ts` 中存在 `StreamingToolExecutor`，表示 Agent 可以在模型流式输出工具块时开始执行工具，而不是等待完整响应结束。

工程价值：

- 降低长任务延迟。
- 允许多个只读工具更早并行。
- 对中断、fallback 和 orphan tool result 提出更高要求。

风险：

- 如果模型 fallback，已经启动的工具结果可能对应旧的 tool_use id，需要丢弃或补齐。
- 如果用户中止，仍要生成匹配的 synthetic tool_result，避免消息协议断裂。
- 流式执行必须严格处理 abort signal。

### 5.5 错误恢复

源码中能看到多类恢复路径：

- 模型高负载触发 fallback model。
- prompt too long 触发 context collapse 或 reactive compact。
- 图片/媒体过大触发压缩或错误提示。
- streaming abort 生成 interruption message。
- 模型异常时补齐缺失 tool_result，避免协议不一致。

工程结论：

- Agent loop 必须把错误当成一等状态。
- 工具调用一旦产生 tool_use，就必须有对应 tool_result 或明确中断结果。
- 对用户报告错误时要区分模型错误、工具错误、用户中止和环境错误。

## 6. Tool 接口：成熟 Agent 的工具契约

`Tool.ts` 是最值得 Harness Engineer 学习的部分。它说明一个成熟工具接口不只是“函数名 + 参数”。

### 6.1 核心调用能力

工具至少需要：

- `name`：稳定工具名。
- `description(input, options)`：根据上下文生成工具描述。
- `inputSchema` / `inputJSONSchema`：结构化输入约束。
- `outputSchema`：结构化输出约束。
- `call(args, context, canUseTool, parentMessage, onProgress)`：实际执行。
- `mapToolResultToToolResultBlockParam`：把工具结果映射成模型可消费的 tool_result。

设计原则：

- 输入必须 schema 化。
- 输出必须结构化。
- 工具结果必须能回灌给模型。
- 工具执行上下文必须包含权限、状态、消息、文件缓存、MCP 连接、预算等运行信息。

### 6.2 安全与并发标记

接口中包含多个关键判断：

- `isReadOnly(input)`：是否只读。
- `isConcurrencySafe(input)`：是否可并发。
- `isDestructive(input)`：是否破坏性操作。
- `isOpenWorld(input)`：是否访问开放世界，例如网络或外部系统。
- `requiresUserInteraction()`：是否需要用户交互。
- `interruptBehavior()`：用户输入到来时取消还是阻塞。

工程规则：

- 读工具可以并行。
- 写工具默认串行。
- 破坏性工具必须有审批。
- 开放世界工具默认高风险。
- 无法证明安全时按不安全处理。

### 6.3 校验与权限

工具接口中包含：

- `validateInput(input, context)`：输入级校验。
- `checkPermissions(input, context)`：工具级权限检查。
- `preparePermissionMatcher(input)`：为权限规则或 hook 条件准备 matcher。
- `toAutoClassifierInput(input)`：为自动安全分类器提供紧凑输入。

工程规则：

- 先校验参数，再做权限判断。
- 权限拒绝要返回给模型，让模型知道失败原因。
- 工具自己的权限逻辑只处理领域特例，通用权限应在 PermissionManager 中统一实现。
- 自动分类器只能辅助，不应替代明确规则。

### 6.4 上下文与输出控制

接口中包含：

- `maxResultSizeChars`：工具结果超过阈值时持久化到文件或只返回预览。
- `shouldDefer`：工具可延迟加载，减少初始工具 schema 占用。
- `alwaysLoad`：关键工具始终加载。
- `backfillObservableInput`：在 hook、transcript、SDK stream 观测前补充派生字段。

工程规则：

- 工具 schema 也是上下文成本。
- 大工具集应使用 ToolSearch 或延迟加载。
- 大结果应截断、摘要或落盘。
- 观测输入可以补充字段，但不能破坏 API-bound 原始输入，避免缓存失效。

### 6.5 UI 与人机共读

接口中还定义了多种渲染方法：

- `renderToolUseMessage`
- `renderToolResultMessage`
- `renderToolUseProgressMessage`
- `renderToolUseRejectedMessage`
- `renderToolUseErrorMessage`
- `getToolUseSummary`
- `getActivityDescription`

工程结论：

- 成熟 Agent 不只服务模型，也服务人类操作者。
- 工具执行过程需要可读状态、进度、错误和摘要。
- 同一个工具结果要同时考虑模型可消费格式和人类可阅读格式。

## 7. tools.ts：工具注册与功能装配

`tools.ts` 展示了成熟 Agent 的工具集合如何被装配。

### 7.1 基础工具

源码中可以看到以下典型工具类别：

- 文件工具：`FileReadTool`、`FileEditTool`、`FileWriteTool`、`NotebookEditTool`。
- 搜索工具：`GlobTool`、`GrepTool`。
- 命令工具：`BashTool`、`PowerShellTool`。
- Web 工具：`WebFetchTool`、`WebSearchTool`。
- MCP 工具：`MCPTool`、`ListMcpResourcesTool`、`ReadMcpResourceTool`、`McpAuthTool`。
- 编排工具：`AgentTool`、`SkillTool`、`ToolSearchTool`。
- 任务工具：`TaskCreateTool`、`TaskGetTool`、`TaskUpdateTool`、`TaskListTool`、`TaskOutputTool`、`TaskStopTool`。
- 协作工具：`SendMessageTool`、`TeamCreateTool`、`TeamDeleteTool`。
- 计划工具：`EnterPlanModeTool`、`ExitPlanModeTool`。
- 工作区工具：`EnterWorktreeTool`、`ExitWorktreeTool`。
- 状态工具：`TodoWriteTool`、`ConfigTool`。

### 7.2 条件工具与特性开关

源码中大量工具受 `feature(...)` 或环境变量控制，例如 cron、remote trigger、monitor、workflow、web browser、context collapse 检查工具等。

工程结论：

- 工具注册表必须支持按环境、权限、功能开关动态装配。
- 不同运行模式不应暴露同样工具集。
- 企业、云端、本地、REPL、SDK、后台 Agent 可以使用不同工具组合。

### 7.3 ToolSearch 与延迟加载

当工具数量很大时，全部工具 schema 会消耗大量上下文。成熟 Agent 会引入 ToolSearch：

- 初始只暴露关键工具和工具搜索能力。
- 需要时由模型搜索并加载特定工具。
- MCP 工具尤其适合延迟加载，因为外部工具数量可能很大。

工程规则：

- 默认工具集要小。
- 低频工具延迟加载。
- 高风险工具即使延迟加载，也必须经过权限系统。

## 8. 权限系统与 Hook

### 8.1 权限上下文

`ToolPermissionContext` 暴露了成熟权限系统需要的状态：

- 当前权限模式。
- 额外工作目录。
- allow / deny / ask 规则。
- bypass permissions 是否可用。
- auto mode 是否可用。
- plan mode 之前的权限状态。
- 是否避免权限弹窗。
- 是否等待自动检查再弹窗。

工程结论：

- 权限不是简单布尔值，而是上下文相关状态机。
- 权限规则要支持来源区分、模式切换和工具级匹配。
- 后台 Agent 不能弹窗时，要自动拒绝或走预设规则。

### 8.2 Hook 的职责

公开文档与源码目录都显示 Hook 是控制 Agent 行为的重要机制。

典型 Hook：

- SessionStart：会话初始化。
- PreToolUse：工具执行前拦截、审计、修改输入。
- PostToolUse：工具执行后记录、截断、触发检查。
- PreCompact / PostCompact：上下文压缩前后处理。
- Stop：会话结束、清理资源、发送通知。
- PostSampling：模型采样后处理。

工程规则：

- Hook 是确定性控制点，不是业务逻辑垃圾桶。
- Hook 要短、快、可测试。
- Hook 失败要有明确策略：阻断、降级或记录后继续。
- 并行 Hook 的顺序不能成为隐含依赖。

## 9. 上下文系统与记忆

成熟 Agent 的上下文不是“拼接 prompt”，而是多源数据的预算化装配。

### 9.1 上下文来源

- 系统提示。
- 用户目标。
- 项目规则文件，例如 `AGENTS.md` / `CLAUDE.md`。
- 用户上下文。
- 系统上下文。
- 对话历史。
- 工具定义。
- 工具结果。
- 附件、图片、文件片段。
- memory 文件。
- skill 描述。
- MCP 资源。

### 9.2 上下文治理

必须治理：

- 大文件读取。
- 大命令输出。
- 重复 memory 注入。
- 工具结果堆积。
- 长会话历史。
- 多媒体附件。
- 大量工具 schema。

建议策略：

- 先搜索，再读取。
- 大文件分段读取。
- 命令输出保留退出码、错误摘要和关键行。
- 项目规则常驻，临时观察可压缩。
- 工具结果超过阈值时只给模型预览和路径。
- 压缩时保留目标、约束、文件、diff、测试结果、未解决风险。

## 10. Subagent、Coordinator 与 Task 系统

源码目录中存在 `coordinator/`、`AgentTool/`、`tasks/`、Team 工具与 Task 工具，说明成熟 Agent 不只是一条单线程对话。

### 10.1 Subagent

Subagent 适合：

- 独立搜索。
- 安全审查。
- 代码审查。
- 文档总结。
- 大范围探索。
- 后台任务。

Subagent 不适合：

- 多个 Agent 同时写同一文件。
- 需要强一致上下文的核心实现。
- 无明确边界的开放式重构。

主 Agent 必须负责整合和最终验收。

### 10.2 Coordinator

Coordinator 的职责是管理多个 Agent：

- 创建任务。
- 分配角色。
- 追踪进度。
- 收集输出。
- 处理取消。
- 汇总结果。
- 避免写冲突。

### 10.3 Task / Todo

Task 与 Todo 是 Agent 的工作记忆和用户可见计划：

- Todo 适合当前任务的短步骤。
- Task 适合长期或后台任务。
- TaskOutput 适合读取后台 Agent 的输出。
- TaskStop 适合取消任务。

工程规则：

- 长任务必须显式追踪状态。
- 计划要随发现更新。
- 最终报告要说明哪些 Task 完成、哪些被取消、哪些失败。

## 11. MCP 与开放工具生态

MCP 让 Agent 可以接入外部系统，例如数据库、浏览器、GitHub、CI、文档库、设计工具和内部服务。

成熟 Agent 对 MCP 的处理要点：

- MCP server connection 是运行上下文的一部分。
- MCP tool 可以像本地工具一样进入 Tool Registry。
- MCP resource 需要独立读取工具。
- MCP 工具可能很多，适合 ToolSearch 延迟加载。
- MCP 授权需要独立处理，例如 `McpAuthTool`。
- MCP 工具的 `_meta`、structured content 要保留给 SDK 消费者。

安全规则：

- 外部系统默认高风险。
- 读写权限分离。
- 生产系统写操作必须审批。
- MCP 工具的描述和 schema 不应无条件全部塞入上下文。

## 12. UI / REPL / 人机共读层

成熟编码 Agent 的终端 UI 不是装饰，而是控制面板。

需要展示：

- 当前模型。
- 当前权限模式。
- 正在执行的工具。
- 工具进度。
- diff。
- 拒绝原因。
- 错误摘要。
- Todo / Task 状态。
- 成本和 token 警告。
- 用户输入请求。

UI 与模型上下文要分离：

- UI-only system message 不应进入 API 消息。
- 人类可读渲染和模型可读 tool_result 要分开。
- transcript 搜索需要真实渲染文本，避免索引内容与屏幕内容不一致。

## 13. AGENTS 核心架构蓝图

可实现的 Harness 模块如下：

```text
User / IDE / CLI
      |
      v
AgentRuntime
  - session lifecycle
  - mode selection
  - config loading
  - model selection
      |
      v
QueryLoop
  - context build
  - model call
  - tool_use collection
  - loop transition
  - stop condition
      |
      v
ToolRegistry <---- ToolSearch
  - local tools
  - MCP tools
  - deferred tools
  - feature-gated tools
      |
      v
PermissionManager <---- HookBus
  - allow / deny / ask
  - plan mode
  - sandbox boundary
  - risk classifier
      |
      v
ToolExecutor
  - validation
  - sequential / parallel execution
  - timeout
  - abort
  - result normalization
      |
      v
ContextManager
  - messages
  - memory
  - attachments
  - compaction
  - tool result budget
      |
      v
TaskManager / SubagentCoordinator
  - todo
  - background tasks
  - subagents
  - team coordination
      |
      v
Verifier + Observer
  - tests
  - build
  - logs
  - telemetry
  - final report
```

### 13.1 AgentRuntime

负责启动和会话生命周期：

- 加载配置。
- 加载项目规则。
- 初始化模型。
- 初始化工具。
- 初始化权限上下文。
- 选择 REPL、SDK、print、后台等运行模式。

### 13.2 QueryLoop

负责主循环：

- 构建本轮上下文。
- 调用模型。
- 收集工具调用。
- 执行工具并回灌结果。
- 处理 fallback、abort、预算、压缩和停止条件。

### 13.3 ContextManager

负责上下文预算：

- 消息规范化。
- memory 注入。
- 附件处理。
- 工具结果预算。
- 自动压缩。
- 反应式压缩。

### 13.4 ToolRegistry

负责工具装配：

- 注册本地工具。
- 注册 MCP 工具。
- 根据功能开关过滤工具。
- 支持 ToolSearch。
- 提供工具查找和别名匹配。

### 13.5 PermissionManager

负责工具执行前的安全判断：

- 处理 allow / deny / ask 规则。
- 处理只读、写入、执行、外部系统权限。
- 支持计划模式。
- 支持自动分类器。
- 输出可回灌给模型的拒绝原因。

### 13.6 ToolExecutor

负责实际执行：

- 参数校验。
- 权限检查。
- 调用工具。
- 收集进度。
- 标准化结果。
- 处理超时、中断和异常。
- 保证每个 tool_use 都有对应结果。

### 13.7 HookBus

负责控制点：

- 触发 SessionStart、PreToolUse、PostToolUse、PreCompact、Stop 等 Hook。
- 支持工具输入修改。
- 支持拒绝工具调用。
- 支持审计和通知。

### 13.8 MemoryManager

负责长期规则与短期记忆：

- 项目规则文件。
- 用户偏好。
- 历史摘要。
- 当前任务状态。
- 已加载 memory 去重。

### 13.9 TaskManager

负责可见计划：

- Todo。
- 长任务。
- 后台任务。
- 任务输出。
- 任务取消。

### 13.10 SubagentCoordinator

负责多 Agent：

- 创建子 Agent。
- 分配上下文。
- 限制工具权限。
- 收集结果。
- 避免写冲突。

### 13.11 Verifier

负责验收：

- 编译。
- 测试。
- lint。
- 类型检查。
- UI 截图。
- diff 检查。
- 人工验收清单。

### 13.12 Observer

负责可观察性：

- 工具调用日志。
- 权限决策日志。
- token 和成本。
- 错误与重试。
- 压缩事件。
- 最终报告。

## 14. 成熟 Agent 的主要功能清单

一个接近 Claude Code / Codex 级别的编码 Agent，主要功能应覆盖：

| 功能 | 说明 |
| --- | --- |
| 文件读取 | 带行号、偏移、大小限制、编码处理 |
| 文件编辑 | 精确替换、diff 展示、拒绝渲染、工作区限制 |
| 文件写入 | 创建或覆盖文件，必须受权限控制 |
| 搜索 | glob、grep、语义搜索或 MCP 代码索引 |
| Shell | 命令执行、超时、退出码、stdout/stderr 截断 |
| Web | fetch/search/browser，默认开放世界高风险 |
| MCP | 外部工具和资源接入 |
| 权限审批 | allow/deny/ask、计划模式、bypass 限制 |
| Hook | 工具前后、会话、压缩、结束控制点 |
| Subagent | 专项任务委派 |
| Task/Todo | 可见计划与后台任务 |
| Skill | 可复用工作流和领域知识 |
| ToolSearch | 大工具集延迟加载 |
| Worktree | 隔离开发分支或工作目录 |
| Context Compact | 长会话压缩与恢复 |
| Cost Tracking | token、费用、预算限制 |
| Telemetry | 事件、trace、错误、性能 |
| Error Recovery | fallback、重试、补齐 tool_result、中断处理 |
| Final Report | 变更、验证、风险、未完成项 |

## 15. Harness Engineer 编码规范

### 15.1 工具设计

- 工具必须结构化定义，不允许万能工具。
- 每个工具必须声明输入 schema、输出结构、是否只读、是否可并发、是否破坏性。
- 工具名必须稳定，重命名时用 alias 保持兼容。
- 工具描述要面向模型说明“何时使用”和“不要何时使用”。
- 工具结果要同时支持模型消费和人类阅读。

### 15.2 权限设计

- 权限默认 fail-closed。
- 读操作默认低风险，写操作默认需要工作区限制。
- Shell、网络、删除、发布、Git push、生产系统写入必须高风险处理。
- 权限拒绝要作为 tool_result 返回给模型。
- bypass permissions 只允许在隔离环境中使用。

### 15.3 并发设计

- 只读工具可并发。
- 写工具默认串行。
- 文件编辑必须避免多个工具同时改同一路径。
- 子 Agent 默认不能共享写权限。
- 流式工具执行必须支持 abort 和 orphan result 清理。

### 15.4 上下文设计

- 大输出必须摘要、落盘或截断。
- 不把整个仓库塞进上下文。
- 项目规则进入 `AGENTS.md`，不依赖临时对话记忆。
- 压缩要保留目标、约束、文件路径、测试结果、错误信息和关键决策。
- Tool schema 也消耗上下文，大工具集要支持延迟加载。

### 15.5 错误处理

- 每类错误要有明确分类：模型、工具、权限、环境、用户中止、业务验证。
- 不允许静默失败。
- 发生模型异常时，要补齐缺失 tool_result。
- fallback 和 retry 要有次数限制。
- 最终报告必须说明失败原因和剩余风险。

### 15.6 验证与交付

- 每次完成必须有验证证据。
- 验证命令未运行时必须说明原因。
- 最终报告必须包含变更文件、验证结果、未验证风险。
- 不能用“应该可以”替代验证结果。

## 16. 反模式

- 只靠 prompt，不做工具契约。
- 用一个万能 Shell 工具承担所有能力。
- 没有权限系统，直接让模型执行命令。
- 不区分只读和写操作。
- 多 Agent 同时写同一文件。
- 把大文件、大日志、大 schema 全塞进上下文。
- 工具失败后继续包装成成功。
- 没有最终验证。
- 把不可见记忆当作工程事实。
- 复制第三方源码而不抽象接口和架构。

## 17. 面向本 Android 项目的落地要求

后续在本仓库实现或使用 Agent Harness 时，应遵守当前项目规则：

- 优先读取根目录 `AGENTS.md`。
- 工作范围主要限制在 `app/src/main/`、`src/main/java/`、`src/main/kotlin/`、`src/main/res/` 和 Manifest/config。
- 不主动分析 `build/`、`.gradle/`、生成文件。
- 修改前复述目标、约束、涉及文件、最小计划和验收标准。
- 中文注释和 `strings.xml` 文案必须使用中文。
- 命令行读写文件时显式使用 UTF-8。
- 以最小变更完成任务。
- 纯文档变更不需要 Android 构建；代码变更需要选择对应编译或测试命令验证。

## 18. 结论

成熟 AGENTS 架构的核心不是“更强模型”，而是“更强 Harness”。

模型负责理解与决策，Harness 负责：

- 给模型正确上下文。
- 给模型合适工具。
- 限制模型的副作用。
- 记录模型的行动。
- 在上下文过载时压缩。
- 在工具失败时恢复。
- 在任务结束时验证。
- 在风险不可控时停止。

Harness Engineer 的目标是把 Agent 设计成一个可执行、可观察、可限制、可恢复、可验证的工程系统。

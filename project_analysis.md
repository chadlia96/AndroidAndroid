# androidAndroid 项目架构评审

## 项目概览

androidAndroid 是一个 Android 自动化代理应用，通过 `AccessibilityService` 获取界面视图树，结合 DeepSeek/Moonshot 等大模型实现 UI 自动化操作。核心流程：**观察界面 → LLM 决策 → 执行动作**。

### 包结构

```
agent/    — Agent 循环、会话管理、Prompt 构建、JSON 解析
data/     — 数据模型
helper/   — 视图树遍历、悬浮窗、节点操作
llm/      — LLM 客户端
ui/       — Fragment、Adapter
utils/    — 应用检测、相机调用
```

---

## 一、架构优点

### 1. 包结构划分清晰

`agent / data / helper / llm / ui / utils` 六个包的职责边界明确，没有循环依赖。一目了然。

### 2. sealed class 运用得当

`ViewNode` 使用 sealed class + 12 个子类建模视图节点类型（Button、EditText、ImageView 等），`Action` 使用 sealed class 建模操作类型（Click、Input、Swipe、Other）。这让 `when` 表达式可以穷尽匹配，编译器自动检查遗漏。

```kotlin
// ViewNodeRenderer.kt — 编译器保证覆盖所有节点类型
return when (node) {
    is ButtonViewNode -> renderButton(node)
    is EditTextViewNode -> renderInput(node)
    is ImageViewNode -> renderImage(node)
    // ... 漏掉任何一个都会编译报错
}
```

### 3. Agent 循环的三阶段设计清晰

`AgentLoop.run()` 方法的 observe → decide → execute 三阶段在代码中有明确分隔和注释，每个阶段的输入输出可追溯：

- **观察阶段**: `NodeOperateHelper.getCurrentRootCopy()` → `PromptHelper.buildStateDescriptionByViewNodes()`
- **决策阶段**: `buildFinalPrompt()` → `LLMManager.chat()` → `AgentJsonParser.parse()`
- **执行阶段**: `executeAction()` → `ActionResult`

### 4. 日志设计好

`LLM_TRACE` 标签覆盖了 Agent 循环的完整生命周期（`AGENT_LOOP_START` → `ROUND_START` → `OBSERVE_DONE` → `MODEL_OUTPUT` → `ACTION_RESULT` → `AGENT_LOOP_END`），调试 LLM 交互时非常有用。

### 5. 内存管理意识

- `NERV` 中对 `AccessibilityService` 的引用使用 `WeakReference`，避免 AccessibilityService 被意外持有导致泄漏。
- `traverseNodeTree` 中递归遍历子节点时在 `finally` 中 `recycle()`。
- `onDestroy` 中清理 `WeakReference` 和协程作用域。

---

## 二、核心问题

### 🔴 问题 1：过度使用 object 单例（最严重）

项目中 **10+ 个类** 都是 Kotlin `object`：

| 类 | 持有的可变状态 |
|---|---|
| `AgentLoop` | `session`, `manualNextRoundClicked` |
| `LLMManager` | 4 个 API key/URL 变量 |
| `NodeOperateHelper` | `serviceRef`, `lastDumpTimeMs`, `lastEventText`, `isEventTextChanged` |
| `FloatingOverlayHelper` | `windowManager`, `binding`, `click1/2/3` |
| `PromptHelper` | 无状态（可接受） |
| `AgentJsonParser` | 无状态（可接受） |
| `ViewNodeTreeHelper` | 无状态（可接受） |
| `ViewNodeRenderer` | 无状态（可接受） |
| `AppUtils` | `timeFormat`（线程不安全！） |
| `AppDetectUtils` | `cachedApps` |

**后果**：

- **完全无法单元测试** — 没有依赖注入，无法 mock 任何依赖
- **全局可变状态** — `NodeOperateHelper.serviceRef` 是全局可写，任何代码都能修改
- **隐式耦合** — `FloatingOverlayHelper` 通过 `androidAndroidApplication.instance` 获取 Context，这是隐藏的依赖链
- `AppUtils.timeFormat` 是 `SimpleDateFormat`，在 object 单例中是共享可变状态，**线程不安全**

**建议**：引入轻量级依赖注入（如 Hilt 或 Koin），或将 `AgentLoop`、`LLMManager`、`NodeOperateHelper`、`FloatingOverlayHelper` 改为类实例，在 `Application` 中创建并注入。

### 🔴 问题 2：AgentLoop 职责过重（306 行）

`AgentLoop` 同时负责：

- Session 生命周期管理
- 手动推进按钮的轮询等待（100ms 忙等循环）
- 候选动作构建
- LLM 调用和响应解析
- 动作执行和失败计数
- Session 状态转换

这是典型的“上帝对象”。建议拆分为：

- `AgentSessionManager` — 会话状态管理
- `AgentDecisionEngine` — Prompt 构建 + LLM 调用 + 响应解析
- `AgentActionExecutor` — 动作分发和执行
- `ManualStepController` — 手动推进逻辑

### 🟡 问题 3：API Key 存储在 assets 中

```kotlin
// LLMManager.kt
val props = Properties()
context.assets.open(CONFIG_FILE).use { input ->
    props.load(input)
}
```

`llm_config.properties` 放在 `assets/` 目录中，会随 APK 一起打包。任何人解包 APK 就能拿到 API Key。生产环境应该从服务端获取或使用安全存储（EncryptedSharedPreferences / Android Keystore）。

### 🟡 问题 4：executeAction 是空实现

```kotlin
// AgentLoop.kt:265-277
private suspend fun executeAction(...): ActionResult {
    Log.w(LLM_TRACE_TAG, "SKIP_EXECUTE(turn=${session.turnCount}): $command")
    return ActionResult(
        action = action,
        success = true,   // ← 始终返回 true
        message = "已记录动作,等待人工手动执行",
        ...
    )
}
```

所有动作都是“假装成功”。这意味着 Agent 循环的核心闭环（**执行 → 观察执行结果 → 根据结果调整下一步动作**）从未真正运行过，循环的正确性无法验证。

### 🟡 问题 5：无 ViewModel，Fragment 持有业务状态

```kotlin
// SecondFragment.kt
private val entries = mutableListOf<Mumble>()    // 配置变更时丢失
private var selectedImageUri: Uri? = null         // 配置变更时丢失
private var previewPopupWindow: PopupWindow? = null
```

旋转屏幕或系统回收 Fragment 后，聊天记录和状态全部丢失。同时 Fragment 中直接处理协程和 LLM 调用，不符合 Android 架构指南推荐的 `UI → ViewModel → Repository` 分层。

### 🟡 问题 6：忙等轮询

```kotlin
// AgentLoop.kt:246-258
private suspend fun waitForManualNextRound(session: AgentSession): Boolean {
    while (session.status == AgentSessionStatus.RUNNING && !manualNextRoundClicked) {
        delay(MANUAL_NEXT_ROUND_POLL_MS)  // 100ms 轮询
    }
}
```

应该使用 `CompletableDeferred` 或 `Channel` 实现事件驱动等待，避免每 100ms 唤醒协程做无效检查。

---

## 三、代码可读性问题

### 1. `ViewNode` 子类单行过长

```kotlin
data class ButtonViewNode(override val message: String, override val extraMessage: String = "", override val className: String = "Button", val clickable: Boolean, val longClickable: Boolean, override val bounds: ViewBounds? = null) : ViewNode(message, extraMessage, className, bounds)
```

这一行超过 250 个字符。建议每个参数单独一行。

### 2. `ViewNodeRenderer.renderTags()` 中的 when 分支冗余

```kotlin
val clickable = when (node) {
    is ButtonViewNode -> node.clickable
    is ImageViewNode -> node.clickable
    is EditTextViewNode -> node.clickable
    is CheckBoxViewNode -> node.clickable
    // ... 8 个分支做完全相同的事
    else -> false
}
```

`clickable` 不是基类属性，每个子类各自定义了同名字段但互不关联。应该在 `ViewNode` 基类中添加 `open val clickable: Boolean get() = false`，让具体子类在构造函数中 override。

### 3. 死代码未清理

- `FirstFragment` 中的 `floatViews`、`iconCount`、`pendingTasks`、`ensureOverlayPermission()` 完全未被使用
- `Mumble` 数据类中的 `mode: Int` 使用魔术数字（0/1），应该用 enum
- `FrontCameraInvoker` 整个类似乎未被调用
- `FirstFragment` 的 `pendingTasks` 和 `mainHandler` 初始化了但从未添加任务

### 4. 中英文混用

代码中提示词、注释、变量名混合了中文和英文（如 `buildFinalPrompt` 整个 prompt 是中文，`FirstFragment` 注释是英文）。建议：

- 面向用户的字符串统一放在 `strings.xml` 中
- 代码标识符和注释统一用英文
- LLM prompt 模板独立到资源文件中管理

### 5. 零测试覆盖

`ExampleUnitTest.kt` 和 `ExampleInstrumentedTest.kt` 只是 Android Studio 生成的模板，没有实际测试。对于一个包含复杂 Agent 逻辑和 JSON 解析的项目，`AgentJsonParser` 和 `PromptHelper` 是最应该被单元测试覆盖的模块。

---

## 四、总结评估

| 维度 | 评分 | 说明 |
|---|---|---|
| 包结构 | ⭐⭐⭐⭐ | 清晰，职责分明 |
| 单例使用 | ⭐ | 严重过度，阻碍测试和可维护性 |
| 分层架构 | ⭐⭐ | 缺少 Repository/ViewModel 层，Fragment 太重 |
| 代码可读性 | ⭐⭐⭐ | sealed class 运用好，但部分文件行太长，死代码未清理 |
| 可测试性 | ⭐ | 全是 object 单例 + 全局状态，无法 mocking |
| 安全性 | ⭐⭐ | API Key 暴露在 APK assets 中 |
| 功能完整度 | ⭐⭐ | Agent 执行是 stub，核心闭环未实现 |

---

## 五、改进建议（按优先级）

1. **引入 DI 框架** — 把 object 单例改为可注入的类实例（Hilt / Koin），解除全局状态耦合
2. **拆分 `AgentLoop`** — 将决策、执行、会话管理、手动推进逻辑解耦到独立类
3. **添加 ViewModel 层** — 把业务逻辑从 Fragment 中抽离到 ViewModel，支持配置变更时保持状态
4. **补充单元测试** — 至少覆盖 `AgentJsonParser`、`PromptHelper`、`ViewNodeRenderer`
5. **API Key 移到安全存储** — 不要在 assets 中硬编码密钥，改用 EncryptedSharedPreferences 或服务端下发
6. **实现 executeAction** — 将 Agent 闭环走通，验证 observe→decide→execute→observe 的反馈循环
7. **清理死代码** — 移除 `FirstFragment` 中未使用的字段和方法，删除或接入 `FrontCameraInvoker`
8. **统一代码风格** — 统一注释语言（建议英文），将 ViewNode 子类参数换行，`mode` 改为 enum
9. **事件驱动替代忙等** — 用 `CompletableDeferred` 或 `Channel` 替代 `waitForManualNextRound` 的 100ms 轮询

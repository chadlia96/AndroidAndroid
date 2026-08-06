# AGENTS.md

## Scope
这是一个使用 Android 无障碍功能,进行界面读取和界面操作的 LLM AGENT 项目

## Editing Rules
- Make the **smallest possible change** for the current task.
- Modify only files and functions directly related to the request.
- **Do not casually refactor or rewrite existing code.**
- **Do not make large structural changes unless explicitly required.**
- 不要添加只做参数转换或调用转发的薄包装函数,除非它明确承载业务语义、资源生命周期、错误处理或稳定的复用边界.


## Before Any Code Change
IMPORTANT: When applicable, prefer using android-studio-index MCP tools for code navigation and refactoring.
Before starting modifications, first restate :
1. the goal you understand
2. the constraints
3. the files/modules likely involved
4. the minimal-change plan
5. the acceptance criteria

## Language & Encoding Rules
- Code comments ,`strings.xml` text values must use **Chinese** unless the task explicitly requires another language.
- 不管中文英文,永远使用英文半角标点符号.
- All files must be read and written in **UTF-8**.
- Do not introduce garbled text or mixed encodings.

## Android Project Guidance
- Prefer preserving current architecture and naming style.
- Focus on the actual business code and accessibility-related logic.
- Avoid touching Gradle/build/test configuration unless the task directly depends on it.
- Gradle 编译命令: `$env:JAVA_HOME='E:\android-studio\jbr'; .\gradlew.bat :app:compileDebugKotlin`

## 文件目录
- Work mainly in a few core source directories such as `app/src/main/`, `src/main/java/`, `src/main/res/`, and related manifest/config files.
- Do **not** spend time reading or analyzing:`build/`,`.gradle/`,generated files, test code unless the task explicitly requires it

## Output Style
- Be precise, short, and implementation-oriented.
- Do not over-explain.
- If something is uncertain, say so clearly instead of guessing.

## Agent Loop Development Principles
- Agent Loop 的目标形态可以解耦为独立模块, 例如：信息采集、Prompt 建立、页面截图、操作执行、流程读取/回放.
- 开发时采用**渐进式解耦**, 先让主链路可验证, 再抽离稳定职责；不要一开始做过量设计.
- 当前阶段允许将“动作执行”降级为“输出 AI 生成的执行内容, 由人工手动操作”, 先验证采集、Prompt、动作协议与流程沉淀能力.

## Documentation Rules
- 将 Agent Loop 的专项设计固定到独立设计文档.
- 文档只记录**已经讨论确认**的内容, 不提前补全未确认设计.
- 对于不可或缺但尚未讨论完成的设计项, 明确标记为 `待定`.

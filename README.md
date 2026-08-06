# androidAndroid

androidAndroid 是一个基于 Android 无障碍服务和大语言模型的实验性 Agent 项目。它可以读取当前界面状态,由模型生成操作指令,并执行启动应用、点击、输入、滑动和返回等动作。

项目目前仍在开发中,主要用于验证界面采集、Prompt 构建、动作协议和 Agent Loop 执行链路。

## 主要功能

- 通过 Android 无障碍服务读取界面节点。
- 使用 Agent Loop 完成观察、决策和执行循环。
- 支持启动应用、点击、输入、滑动和返回操作。
- 支持普通文本对话和图片内容识别。
- 通过悬浮窗展示状态和辅助操作。

## 环境要求

- Android Studio。
- JDK 11 或 Android Studio 自带 JBR。
- Android SDK 35。
- Android 11 及以上设备,最低 API 30。

## 本地配置

复制示例配置文件:

```powershell
Copy-Item llm_config.example.json llm_config.json
```

然后填写本地 API Key:

```json
{
  "deepseekApiKey": "your-deepseek-api-key",
  "moonshotApiKey": "your-moonshot-api-key"
}
```

`llm_config.json` 已被 Git 忽略,请勿提交真实密钥。API Key 会被写入本地构建产物,不要使用个人密钥构建并公开分发 APK。

## 运行

1. 使用 Android Studio 打开项目并完成 Gradle Sync。
2. 连接 Android 11 及以上设备。
3. 构建并安装应用。
4. 根据应用提示授予无障碍和悬浮窗权限。
5. 输入任务,观察 Agent 的执行过程。


## 项目结构

```text
app/src/main/java/com/android/androidAndroid/
├── accessibility/  无障碍信息采集与动作执行
├── agent/          Agent Loop、Prompt 和动作协议
├── llm/            模型调用
└── ui/             主界面、聊天和悬浮窗
```

Agent Loop 的详细设计参见 [AGENT_LOOP_DESIGN.md](AGENT_LOOP_DESIGN.md)。

## 隐私提醒

本项目可能读取当前界面文本、通知内容和图片,并将任务相关信息发送给第三方模型服务。请仅在了解数据流向并获得必要授权的情况下使用,不要处理敏感或未经授权的数据。


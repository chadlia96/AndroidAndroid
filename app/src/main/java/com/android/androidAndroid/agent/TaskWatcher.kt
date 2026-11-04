package com.android.androidAndroid.agent

import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper
import kotlinx.coroutines.channels.SendChannel

object TaskWatcher {
    private var responseRound = 0
    @Volatile
    private var output: SendChannel<String>? = null

    fun bindOutput(output: SendChannel<String>) {
        this.output = output
    }

    fun finish(session: AgentSession) {
        when (session.status) {
            AgentSessionStatus.FAILED -> output?.trySend("任务失败: ${session.metadata["last_error"].orEmpty()}")
            AgentSessionStatus.CANCELLED -> output?.trySend("任务已取消")
            else -> Unit
        }
    }

    fun unbindOutput(output: SendChannel<String>) {
        if (this.output === output) {
            this.output = null
        }
    }

    fun start(goal: String) {
        responseRound = 0
        FloatingTextOverlayHelper.show()
        FloatingTextOverlayHelper.setLog("")
        FloatingTextOverlayHelper.setTitle("发起任务 $goal")
        output?.trySend("发起任务 $goal")
    }

    fun onLLMRequest() {
        FloatingTextOverlayHelper.isThinking(true)
    }
    fun onLLMResponse(response: String) {
        FloatingTextOverlayHelper.isThinking(false)
        val message = "${responseRound++} : $response"
        FloatingTextOverlayHelper.setLog(message)
        output?.trySend(message)
    }

    fun onSuccess() {
        FloatingTextOverlayHelper.setTitle("任务成功")
        output?.trySend("任务成功")
    }

    fun release() {
        FloatingTextOverlayHelper.release()
    }
}

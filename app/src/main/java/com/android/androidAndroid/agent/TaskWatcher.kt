package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.AgentDecision
import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper

object TaskWatcher {
    private var responseRound = 0

    fun start(goal: String) {
        responseRound = 0
        FloatingTextOverlayHelper.show()
        FloatingTextOverlayHelper.setLog("")
        FloatingTextOverlayHelper.setTitle("发起任务 $goal")
    }

    fun onLLMRequest() {
        FloatingTextOverlayHelper.isThinking(true)
    }
    fun onLLMResponse(response: String) {
        FloatingTextOverlayHelper.isThinking(false)
        FloatingTextOverlayHelper.setLog("${responseRound++} : $response")
    }

    fun onSuccess() {
        FloatingTextOverlayHelper.setTitle("任务成功")
    }

    fun release() {
        FloatingTextOverlayHelper.release()
    }
}

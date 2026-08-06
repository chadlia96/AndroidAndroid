package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.action.launchApp.InstalledApp
import com.android.androidAndroid.agent.action.launchApp.LaunchAppResponseParser
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchAppSelectionTest {

    private val apps = listOf(
        InstalledApp(appName1 = "地图", packageName = "com.example.maps"),
        InstalledApp(appName1 = "相册", packageName = "com.example.photos")
    )
    @Test
    fun buildLaunchAppPrompt_containsGoalCandidatesAndRequiredFormat() {
        val prompt = AgentPromptBuilder.buildLaunchAppPrompt("打开相册查看图片", apps)

        assertTrue(prompt.contains("打开相册查看图片"))
        assertTrue(prompt.contains("地图"))
        assertTrue(prompt.contains("com.example.maps"))
        assertTrue(prompt.contains("相册"))
        assertTrue(prompt.contains("com.example.photos"))
        assertTrue(prompt.contains("{\"appName\":\"候选列表中的应用名\",\"packageName\":\"候选列表中的包名\"}"))
    }

    @Test
    fun parseApk_acceptsJsonAndMarkdownCodeBlock() {
        assertEquals(
            InstalledApp(appName1 = "相册", packageName = "com.example.photos"),
            LaunchAppResponseParser.parseApk(
                "{\"appName\":\"相册\",\"packageName\":\"com.example.photos\"}",
                apps
            )
        )
        assertEquals(
            InstalledApp(appName1 = "地图", packageName = "com.example.maps"),
            LaunchAppResponseParser.parseApk(
                "```json\n{\"appName\":\"地图\",\"packageName\":\"com.example.maps\"}\n```",
                apps
            )
        )
    }

    @Test
    fun parseApk_rejectsEmptyNullMalformedAndUnknownPackage() {
        assertNull(LaunchAppResponseParser.parseApk("{\"appName\":\"\",\"packageName\":\"\"}", apps))
        assertNull(LaunchAppResponseParser.parseApk("{\"appName\":null,\"packageName\":null}", apps))
        assertNull(LaunchAppResponseParser.parseApk("不是JSON", apps))
        assertNull(
            LaunchAppResponseParser.parseApk(
                "{\"appName\":\"相册\",\"packageName\":\"com.example.unknown\"}",
                apps
            )
        )
    }
}

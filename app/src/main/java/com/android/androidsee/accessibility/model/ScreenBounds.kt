package com.android.androidAndroid.accessibility.model

data class ScreenBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun describe(): String = "位置=($left,$top), 大小=${width}x${height}"
}

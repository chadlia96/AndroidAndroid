package com.android.androidAndroid.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object TouchUtils {

    // 由节点 bounds 计算中心点,作为坐标触摸的目标位置。
    fun centerOf(node: AccessibilityNodeInfo): PointF? {
        return try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                null
            } else {
                PointF(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        } catch (t: Throwable) {
            null
        }
    }

    // 在指定位置执行一次触摸点击(按下并抬起),返回是否派发成功。
    fun performTouch(service: AccessibilityService, point: PointF): Boolean {
        return try {
            val path = Path().apply {
                moveTo(point.x, point.y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                .build()
            service.dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            false
        }
    }

    private const val TAP_DURATION_MS = 60L
}
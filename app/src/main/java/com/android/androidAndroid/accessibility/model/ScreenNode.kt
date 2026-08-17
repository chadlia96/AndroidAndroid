package com.android.androidAndroid.accessibility.model



sealed class ScreenNode(
    open val message: String,
    open val extraMessage: String = "",
    open val className: String,
    open val bounds: ScreenBounds?
)

data class TextScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "TextView", val clickable: Boolean = false, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class ButtonScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "Button", val clickable: Boolean, val longClickable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class ImageScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "ImageView", val clickable: Boolean, val longClickable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class EditTextScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "EditText", val clickable: Boolean, val editable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class RecyclerScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "RecyclerView", val scrollable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class CheckBoxScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "CheckBox", val clickable: Boolean, val checkable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class SwitchScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "Switch", val clickable: Boolean, val checkable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class RadioButtonScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "RadioButton", val clickable: Boolean, val checkable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class SeekBarScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "SeekBar", val clickable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class WebScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "WebView", override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class ContainerScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "Container", val clickable: Boolean, val longClickable: Boolean, override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)
data class UnknownScreenNode(override val message: String, override val extraMessage: String = "", override val className: String = "Unknown", override val bounds: ScreenBounds? = null) : ScreenNode(message, extraMessage, className, bounds)

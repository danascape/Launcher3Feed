package dev.danascape.launcher3feed.core

object OverlayBridge {
    private var callback: OverlayBridgeCallback? = null

    fun setCallback(callback: OverlayBridgeCallback?) {
        this.callback = callback
    }

    interface OverlayBridgeCallback {
        fun applyNewTheme(value: String)
        fun applyCompactCard(value: Boolean)
        fun applyNewTransparency(value: Float)
        fun onClientMessage(action: String)
    }
}

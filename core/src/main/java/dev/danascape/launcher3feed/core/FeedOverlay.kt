package dev.danascape.launcher3feed.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.libraries.gsa.d.a.OverlayController

/**
 * Base for the launcher -1 screen. Apps extend this and provide their content
 * through [onCreateContentView].
 */
abstract class FeedOverlay @JvmOverloads constructor(
    private val context: Context,
    theme: Int = R.style.Theme_Launcher3Feed_Overlay,
    windowTheme: Int = R.style.Theme_Launcher3Feed_OverlayWindow
) : OverlayController(context, theme, windowTheme),
    OverlayBridge.OverlayBridgeCallback {

    protected lateinit var rootView: View
        private set

    companion object {
        private const val TAG = "FeedOverlay"
    }

    /**
     * View shown inside the overlay panel. It is attached to
     * [container] by the base class.
     */
    protected abstract fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup): View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing overlay")

        if (container != null) {
            rootView = onCreateContentView(LayoutInflater.from(context), container)
            container.addView(rootView)
            Log.d(TAG, "Content view added to container")
        } else {
            Log.e(TAG, "Container is null!")
        }

        OverlayBridge.setCallback(this)

        Log.d(TAG, "Overlay created successfully")
    }

    override fun onScroll(progress: Float) {
        super.onScroll(progress)
        Log.d(TAG, "onScroll: $progress")

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDestroy() {
        super.onDestroy()
        OverlayBridge.setCallback(null)
        Log.d(TAG, "Overlay destroyed")
    }

    // OverlayBridge callback methods
    override fun applyNewTheme(value: String) {
        Log.d(TAG, "applyNewTheme: $value")
    }

    override fun applyCompactCard(value: Boolean) {
        Log.d(TAG, "applyCompactCard: $value")
    }

    override fun applyNewTransparency(value: Float) {
        Log.d(TAG, "applyNewTransparency: $value")

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onClientMessage(action: String) {
        Log.d(TAG, "onClientMessage: $action")
    }
}

package dev.danascape.launcher3feed.helloworld.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.libraries.gsa.d.a.OverlayController
import dev.danascape.launcher3feed.helloworld.FeedApp
import dev.danascape.launcher3feed.helloworld.R

class OverlayView(private val context: Context) : OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    OverlayBridge.OverlayBridgeCallback {

    private lateinit var rootView: View

    companion object {
        private const val TAG = "OverlayView"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing OverlayView")

        if (container != null) {
            rootView = View.inflate(context, R.layout.overlay_layout, null)
            container.addView(rootView)
            Log.d(TAG, "Content view added to container")
        } else {
            Log.e(TAG, "Container is null!")
        }

        // Register callback
        FeedApp.bridge.setCallback(this)

        Log.d(TAG, "OverlayView created successfully")
    }

    override fun onScroll(progress: Float) {
        super.onScroll(progress)
        Log.d(TAG, "onScroll: $progress")

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDestroy() {
        super.onDestroy()
        FeedApp.bridge.setCallback(null)
        Log.d(TAG, "OverlayView destroyed")
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
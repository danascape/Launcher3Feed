package com.prauga.pvotfeed.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.libraries.gsa.d.a.OverlayController
import com.prauga.pvotfeed.FeedApp
import com.prauga.pvotfeed.R

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

        // Update background transparency based on scroll
        val alpha = (progress * 0.95f * 255).toInt()
        val color = Color.argb(alpha, 240, 240, 240)
        window?.setBackgroundDrawable(ColorDrawable(color))
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
        val alpha = (value * 255).toInt()
        val color = Color.argb(alpha, 240, 240, 240)
        window?.setBackgroundDrawable(ColorDrawable(color))
    }

    override fun onClientMessage(action: String) {
        Log.d(TAG, "onClientMessage: $action")
    }
}
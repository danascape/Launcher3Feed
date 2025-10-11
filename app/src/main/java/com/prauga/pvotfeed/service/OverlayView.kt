package com.prauga.pvotfeed.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import com.google.android.libraries.gsa.d.a.OverlayController
import com.prauga.pvotfeed.FeedApp
import com.prauga.pvotfeed.R

class OverlayView(private val context: Context) : OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    OverlayBridge.OverlayBridgeCallback {

    private lateinit var rootView: View
    private lateinit var btnEdit: Button
    private lateinit var btnDone: Button

    private var isEditMode: Boolean = false

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

        initEditHeader()
        setupTouchListeners()

        // Register callback
        FeedApp.bridge.setCallback(this)

        Log.d(TAG, "OverlayView created successfully")
    }

    private fun setupTouchListeners() {
        // Long press to show buttons when not in edit mode
        rootView.setOnLongClickListener {
            if (!isEditMode) {
                toggleEditButtons(true)
                Toast.makeText(context, "Edit mode enabled", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        // Regular click to hide buttons when in edit mode
        rootView.setOnClickListener {
            if (isEditMode) {
                toggleEditButtons(false)
                Toast.makeText(context, "Edit mode disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initEditHeader() {
        val btnEdit = rootView.findViewById<View>(R.id.btnEdit)
        val btnDone = rootView.findViewById<View>(R.id.btnDone)

        btnEdit.setOnClickListener { anchor ->
            val popupMenu = PopupMenu(this, anchor)
            popupMenu.menuInflater.inflate(R.menu.actions_edit_menu, popupMenu.menu)
            popupMenu.show()
            popupMenu.setOnMenuItemClickListener { it ->
                when(it.itemId) {
                    R.id.action_add_widget -> {
                        Log.d(TAG, "Add widget clicked")
                        true
                    }
                    R.id.action_customise -> {
                        Log.d(TAG, "Customise clicked")
                        true
                    }
                    else -> false
                }
            }
        }

        btnDone.setOnClickListener {
            Toast.makeText(context, "Done button clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEditButtons(show: Boolean) {
        val btnEdit = rootView.findViewById<View>(R.id.btnEdit)
        val btnDone = rootView.findViewById<View>(R.id.btnDone)

        if (show) {
            // Show the buttons
            btnEdit.visibility = View.VISIBLE
            btnDone.visibility = View.VISIBLE
            isEditMode = true
        } else {
            // Hide the buttons
            btnEdit.visibility = View.GONE
            btnDone.visibility = View.GONE
            isEditMode = false
        }
    }

    override fun onScroll(progress: Float) {
        super.onScroll(progress)
        Log.d(TAG, "onScroll: $progress")

        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Calculate alpha based on progress (0.0 to 1.0)
        // Progress ranges from 0 (fully closed) to 1 (fully open)
        val alpha = (progress * 255).toInt().coerceIn(0, 255)

        val color = if (isDarkMode) {
            Color.argb(alpha, 38, 38, 38)
        } else {
            Color.argb(alpha, 192, 192, 192)
        }

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

        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Calculate alpha based on transparency value
        val alpha = (value * 255).toInt().coerceIn(0, 255)

        val color = if (isDarkMode) {
            Color.argb(alpha, 38, 38, 38)
        } else {
            Color.argb(alpha, 192, 192, 192)
        }

        window?.setBackgroundDrawable(ColorDrawable(color))
    }

    override fun onClientMessage(action: String) {
        Log.d(TAG, "onClientMessage: $action")
    }
}
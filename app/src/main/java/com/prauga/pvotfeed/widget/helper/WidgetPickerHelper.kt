package com.prauga.pvotfeed.widget.helper

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.prauga.pvotfeed.widget.manager.WidgetHostManager

/**
 * Helper class to handle widget picker operations
 * Manages the flow of selecting and configuring widgets
 */
class WidgetPickerHelper(private val context: Context) {

    companion object {
        private const val TAG = "WidgetPickerHelper"
    }

    private var pendingWidgetId: Int = -1
    private var onWidgetSelectedCallback: ((Int) -> Unit)? = null

    /**
     * Launch the system widget picker
     * For system apps, we can directly launch the picker activity
     */
    fun launchWidgetPicker(
        activity: Activity,
        widgetHostManager: WidgetHostManager,
        onSelected: (Int) -> Unit
    ) {
        onWidgetSelectedCallback = onSelected

        // Allocate a widget ID
        pendingWidgetId = widgetHostManager.allocateWidgetId()

        try {
            // Create the picker intent
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)

                // For system apps on Android 11+, we might need to specify the picker component
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Try to use the system widget picker directly
                    setComponent(ComponentName("com.android.settings",
                        "com.android.settings.appwidget.AppWidgetPickActivity"))
                }
            }

            // Add custom widgets if needed (optional)
            // For custom widgets, we would normally add AppWidgetProviderInfo objects here
            // Since we're not adding custom widgets, we can skip this or pass empty lists
            // Note: These are optional and can be omitted entirely for standard widget picker

            activity.startActivityForResult(pickIntent, WidgetHostManager.REQUEST_PICK_WIDGET)
            Log.d(TAG, "Widget picker launched with ID: $pendingWidgetId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch widget picker", e)
            // Fallback: Try alternative approach
            launchAlternativeWidgetPicker(activity)
        }
    }

    /**
     * Alternative widget picker for when the default doesn't work
     */
    private fun launchAlternativeWidgetPicker(activity: Activity) {
        try {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            }

            // Try without specific component
            activity.startActivityForResult(intent, WidgetHostManager.REQUEST_PICK_WIDGET)
            Log.d(TAG, "Alternative widget picker launched")

        } catch (e: Exception) {
            Log.e(TAG, "Alternative widget picker also failed", e)
            // Last resort: Show available widget providers
            showAvailableWidgets()
        }
    }

    /**
     * Show list of available widget providers (fallback)
     */
    private fun showAvailableWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val providers = appWidgetManager.installedProviders

        Log.d(TAG, "Available widget providers:")
        providers.forEach { info ->
            Log.d(TAG, "- ${info.label} (${info.provider})")
        }

        // You could create a custom dialog here to show available widgets
        // For now, just log them
    }

    /**
     * Handle activity result from widget picker
     */
    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        when (requestCode) {
            WidgetHostManager.REQUEST_PICK_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (widgetId != -1) {
                        Log.d(TAG, "Widget selected with ID: $widgetId")
                        onWidgetSelectedCallback?.invoke(widgetId)
                        return true
                    }
                } else {
                    Log.d(TAG, "Widget selection cancelled")
                }
            }
            WidgetHostManager.REQUEST_CREATE_WIDGET -> {
                Log.d(TAG, "Widget configuration result received")
                return true
            }
        }
        return false
    }

    /**
     * Check if the device supports widgets
     */
    fun isWidgetSupported(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.installedProviders.isNotEmpty()
    }

    /**
     * Get number of available widgets
     */
    fun getAvailableWidgetCount(): Int {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.installedProviders.size
    }

    /**
     * Clear pending operations
     */
    fun clearPending() {
        pendingWidgetId = -1
        onWidgetSelectedCallback = null
    }
}
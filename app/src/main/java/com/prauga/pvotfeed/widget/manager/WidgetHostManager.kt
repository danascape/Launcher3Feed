package com.prauga.pvotfeed.widget.manager

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Manages widget hosting functionality for the overlay
 * Handles widget lifecycle, binding, and updates
 */
class WidgetHostManager(private val context: Context) {

    companion object {
        private const val TAG = "WidgetHostManager"
        const val WIDGET_HOST_ID = 1024  // Made public for consistency across the app
        const val REQUEST_PICK_WIDGET = 100
        const val REQUEST_CREATE_WIDGET = 101
        const val REQUEST_PICK_APPWIDGET = 9  // Native picker request code
        const val REQUEST_CREATE_APPWIDGET = 10
        const val REQUEST_BIND_APPWIDGET = 11
    }

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    // Custom AppWidgetHost that handles updates
    private val appWidgetHost: AppWidgetHost = object : AppWidgetHost(context, WIDGET_HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            Log.d(TAG, "Creating widget view for ID: $appWidgetId")
            return super.onCreateView(context, appWidgetId, appWidget)
        }

        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
            super.onProviderChanged(appWidgetId, appWidget)
            Log.d(TAG, "Widget provider changed for ID: $appWidgetId")

            // Update the widget view if it exists
            activeWidgets[appWidgetId]?.let { view ->
                if (appWidget != null) {
                    view.setAppWidget(appWidgetId, appWidget)
                    view.updateAppWidget(null)
                }
            }
        }
    }

    // Map to store widget IDs and their views
    private val activeWidgets = mutableMapOf<Int, AppWidgetHostView>()

    init {
        // Start listening for widget updates
        try {
            appWidgetHost.startListening()
            Log.d(TAG, "AppWidgetHost started listening for updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening for widget updates", e)
        }
    }

    /**
     * Allocate a new widget ID for widget selection
     */
    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    /**
     * Delete a widget ID (for cleanup when cancelled)
     */
    fun deleteWidgetId(widgetId: Int) {
        try {
            appWidgetHost.deleteAppWidgetId(widgetId)
            activeWidgets.remove(widgetId)
            Log.d(TAG, "Deleted widget ID: $widgetId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete widget ID: $widgetId", e)
        }
    }

    /**
     * Create an intent to launch the widget picker
     */
    fun createWidgetPickerIntent(widgetId: Int): Intent {
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return pickIntent
    }

    /**
     * Handle the result from widget picker
     */
    fun handleWidgetPickerResult(
        resultCode: Int,
        data: Intent?,
        container: ViewGroup,
        onConfigRequired: (Intent) -> Unit
    ): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "Widget picker cancelled or invalid data")
            return false
        }

        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) {
            Log.e(TAG, "Invalid widget ID received")
            return false
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)

        if (appWidgetInfo == null) {
            Log.e(TAG, "Failed to get widget info for ID: $widgetId")
            return false
        }

        // Check if widget needs configuration
        if (appWidgetInfo.configure != null) {
            Log.d(TAG, "Widget requires configuration")
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            onConfigRequired(configIntent)
            return true
        }

        // Widget doesn't need configuration, add it directly
        addWidgetToContainer(widgetId, appWidgetInfo, container)
        return true
    }

    /**
     * Handle configuration result and add widget
     */
    fun handleConfigurationResult(
        resultCode: Int,
        data: Intent?,
        container: ViewGroup
    ): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "Widget configuration cancelled")
            return false
        }

        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) {
            Log.e(TAG, "Invalid widget ID from configuration")
            return false
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
        if (appWidgetInfo != null) {
            addWidgetToContainer(widgetId, appWidgetInfo, container)
            return true
        }

        return false
    }

    /**
     * Add a widget to the specified container
     */
    fun addWidgetToContainer(
        widgetId: Int,
        widgetInfo: AppWidgetProviderInfo,
        container: ViewGroup
    ) {
        Log.d(TAG, "Adding widget: ${widgetInfo.label}")

        try {
            // Create the widget view
            val widgetView = appWidgetHost.createView(context, widgetId, widgetInfo)

            // Set layout parameters
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            widgetView.layoutParams = layoutParams

            // Enable auto-advance for widgets that support it (like photo frames)
            widgetView.setAppWidget(widgetId, widgetInfo)

            // Store the widget
            activeWidgets[widgetId] = widgetView

            // Add to container
            container.addView(widgetView)

            // Trigger initial update
            widgetView.updateAppWidget(null)

            // Ensure the widget host is still listening
            ensureHostListening()

            Toast.makeText(context, "Widget added: ${widgetInfo.label}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Widget added successfully with ID: $widgetId, updates enabled")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add widget to container", e)
            Toast.makeText(context, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Ensure the widget host is listening for updates
     */
    private fun ensureHostListening() {
        try {
            // This will restart listening if it was stopped
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring host listening", e)
        }
    }

    /**
     * Remove a widget from the container
     */
    fun removeWidget(widgetId: Int, container: ViewGroup) {
        val widgetView = activeWidgets[widgetId]
        if (widgetView != null) {
            container.removeView(widgetView)
            activeWidgets.remove(widgetId)
            appWidgetHost.deleteAppWidgetId(widgetId)
            Log.d(TAG, "Widget removed: $widgetId")
        }
    }

    /**
     * Remove all widgets
     */
    fun removeAllWidgets(container: ViewGroup) {
        for ((widgetId, _) in activeWidgets) {
            removeWidget(widgetId, container)
        }
    }

    /**
     * Get list of active widget IDs
     */
    fun getActiveWidgetIds(): List<Int> {
        return activeWidgets.keys.toList()
    }

    /**
     * Update widget sizes when container changes
     */
    fun updateWidgetSizes() {
        for ((_, widgetView) in activeWidgets) {
            widgetView.updateAppWidgetSize(null,
                widgetView.width, widgetView.height,
                widgetView.width, widgetView.height)
        }
    }

    /**
     * Force update all widgets (useful for refresh)
     */
    fun updateAllWidgets() {
        Log.d(TAG, "Forcing update on all ${activeWidgets.size} widgets")
        for ((widgetId, widgetView) in activeWidgets) {
            try {
                widgetView.updateAppWidget(null)
                Log.d(TAG, "Updated widget ID: $widgetId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget ID: $widgetId", e)
            }
        }
    }

    /**
     * Resume listening for updates (call in onResume)
     */
    fun onResume() {
        try {
            appWidgetHost.startListening()
            updateAllWidgets()
            Log.d(TAG, "Resumed listening for widget updates")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming widget updates", e)
        }
    }

    /**
     * Pause listening for updates (call in onPause)
     */
    fun onPause() {
        try {
            // Don't stop listening in onPause to keep widgets updating
            // Only stop in onDestroy
            Log.d(TAG, "onPause called but keeping widget updates active")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause", e)
        }
    }

    /**
     * Clean up resources
     */
    fun onDestroy() {
        try {
            appWidgetHost.stopListening()
            activeWidgets.clear()
            Log.d(TAG, "WidgetHostManager destroyed, stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying widget host", e)
        }
    }
}
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
        private const val WIDGET_HOST_ID = 1024
        const val REQUEST_PICK_WIDGET = 100
        const val REQUEST_CREATE_WIDGET = 101
    }

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, WIDGET_HOST_ID)

    // Map to store widget IDs and their views
    private val activeWidgets = mutableMapOf<Int, AppWidgetHostView>()

    init {
        // Start listening for widget updates
        appWidgetHost.startListening()
    }

    /**
     * Allocate a new widget ID for widget selection
     */
    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
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

        // Create the widget view
        val widgetView = appWidgetHost.createView(context, widgetId, widgetInfo)

        // Set layout parameters
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        widgetView.layoutParams = layoutParams

        // Store the widget
        activeWidgets[widgetId] = widgetView

        // Add to container
        container.addView(widgetView)

        Toast.makeText(context, "Widget added: ${widgetInfo.label}", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Widget added successfully with ID: $widgetId")
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
     * Clean up resources
     */
    fun onDestroy() {
        appWidgetHost.stopListening()
        activeWidgets.clear()
        Log.d(TAG, "WidgetHostManager destroyed")
    }
}
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

        // Shared thread pool executor for widget RemoteViews inflation (like Trebuchet)
        private val WIDGET_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            java.util.concurrent.ThreadFactory { r ->
                Thread(r, "widget-inflation").apply {
                    priority = Thread.NORM_PRIORITY
                }
            }
        )
    }

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    // Custom AppWidgetHost that handles updates
    private val appWidgetHost: AppWidgetHost = object : AppWidgetHost(context, WIDGET_HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            Log.d(TAG, "Creating widget view for ID: $appWidgetId, provider: ${appWidget?.provider}")

            // Create AppWidgetHostView with proper executor configuration (like Trebuchet launcher)
            return object : AppWidgetHostView(context) {
                init {
                    // KEY FIX: Set shared executor for background thread inflation
                    // This allows RemoteViews to properly access widget provider's package resources
                    // This is the same approach used by Trebuchet launcher
                    setExecutor(WIDGET_EXECUTOR)

                    // Enable clipping to outline for proper rendering
                    clipToOutline = true

                    Log.d(TAG, "AppWidgetHostView created for ID $appWidgetId with shared thread pool executor")
                }

                override fun getAppWidgetInfo(): AppWidgetProviderInfo? {
                    return appWidget
                }

                override fun getErrorView(): android.view.View? {
                    // Provide custom error view
                    val errorView = android.widget.TextView(context)
                    errorView.text = "⚠ Widget Error\n${appWidget?.label ?: "Unknown"}"
                    errorView.gravity = android.view.Gravity.CENTER
                    errorView.setTextColor(0xFF666666.toInt())
                    errorView.setBackgroundColor(0xFFEEEEEE.toInt())
                    errorView.setPadding(32, 32, 32, 32)
                    return errorView
                }

                override fun onAttachedToWindow() {
                    super.onAttachedToWindow()
                    Log.d(TAG, "Widget ID $appWidgetId attached to window")
                }

                override fun onDetachedFromWindow() {
                    super.onDetachedFromWindow()
                    Log.d(TAG, "Widget ID $appWidgetId detached from window")
                }

                override fun onWindowVisibilityChanged(visibility: Int) {
                    super.onWindowVisibilityChanged(visibility)
                    val visibilityStr = when(visibility) {
                        android.view.View.VISIBLE -> "VISIBLE"
                        android.view.View.INVISIBLE -> "INVISIBLE"
                        android.view.View.GONE -> "GONE"
                        else -> "UNKNOWN($visibility)"
                    }
                    Log.d(TAG, "Widget ID $appWidgetId window visibility changed to: $visibilityStr")
                }

                override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                    try {
                        super.onLayout(changed, left, top, right, bottom)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Error during widget layout for ID $appWidgetId", e)
                        // Switch to error view on layout failure
                        post {
                            try {
                                val errorRemoteViews = android.widget.RemoteViews(
                                    appWidget?.provider?.packageName ?: context.packageName,
                                    0
                                )
                                updateAppWidget(errorRemoteViews)
                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to switch to error view", ex)
                            }
                        }
                    }
                }

                override fun updateAppWidget(remoteViews: android.widget.RemoteViews?) {
                    if (remoteViews == null) {
                        Log.d(TAG, "updateAppWidget called with null RemoteViews for ID $appWidgetId")
                        super.updateAppWidget(null)
                        return
                    }

                    try {
                        Log.d(TAG, "Updating widget ID $appWidgetId with RemoteViews from package: ${remoteViews.`package`}")
                        super.updateAppWidget(remoteViews)
                        Log.d(TAG, "Widget ID $appWidgetId updated successfully")
                    } catch (e: android.content.res.Resources.NotFoundException) {
                        Log.e(TAG, "Resource not found for widget ID $appWidgetId: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating widget ID $appWidgetId: ${e.message}", e)
                    }
                }
            }
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
        container: ViewGroup,
        showToast: Boolean = true
    ) {
        Log.d(TAG, "Adding widget: ${widgetInfo.label} from package: ${widgetInfo.provider.packageName}")
        Log.d(TAG, "Widget class: ${widgetInfo.provider.className}")

        try {
            // Create the widget view with error handling
            val widgetView = try {
                appWidgetHost.createView(context, widgetId, widgetInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create widget view for ${widgetInfo.label}", e)
                // Clean up the allocated widget ID
                appWidgetHost.deleteAppWidgetId(widgetId)
                if (showToast) {
                    Toast.makeText(context, "Couldn't load widget: ${widgetInfo.label}\n${e.message}", Toast.LENGTH_LONG).show()
                }
                return
            }

            // Calculate proper widget dimensions
            val widgetDimensions = calculateWidgetDimensions(widgetInfo, container)
            Log.d(TAG, "Widget dimensions: ${widgetDimensions.width}x${widgetDimensions.height}")

            // Set layout parameters with calculated dimensions
            // Use MATCH_PARENT for width and calculated height to ensure proper scrolling
            val layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                widgetDimensions.height
            ).apply {
                // Add margin for better spacing
                val margin = (16 * context.resources.displayMetrics.density).toInt()
                val isFirstWidget = container.childCount == 0
                val topMargin = if (isFirstWidget) 0 else margin
                setMargins(margin, topMargin, margin, margin)
            }
            widgetView.layoutParams = layoutParams

            // Set minimum width and height
            widgetView.minimumWidth = widgetDimensions.minWidth
            widgetView.minimumHeight = widgetDimensions.minHeight

            // Tag the widget view with its ID for easy lookup
            widgetView.tag = widgetId

            // Store the widget
            activeWidgets[widgetId] = widgetView

            // Ensure the widget host is listening BEFORE adding the view
            ensureHostListening()

            // Add to container FIRST - this attaches the view to the window
            container.addView(widgetView)

            // NOW set the app widget info - this will trigger the initial RemoteViews update
            // The view needs to be attached to the window before receiving updates
            widgetView.setAppWidget(widgetId, widgetInfo)
            Log.d(TAG, "Widget ID $widgetId setAppWidget called, initial update should trigger automatically")

            // Update widget size after layout
            widgetView.post {
                try {
                    updateWidgetSize(widgetView, widgetId, widgetInfo)
                    Log.d(TAG, "Widget ID $widgetId size updated after layout")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget size for ID: $widgetId", e)
                }
            }

            if (showToast) {
                Toast.makeText(context, "Widget added: ${widgetInfo.label}", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "Widget added successfully with ID: $widgetId, size: ${widgetDimensions.width}x${widgetDimensions.height}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add widget to container", e)
            if (showToast) {
                Toast.makeText(context, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Calculate appropriate dimensions for a widget based on its requirements
     */
    private fun calculateWidgetDimensions(
        widgetInfo: AppWidgetProviderInfo,
        container: ViewGroup
    ): WidgetDimensions {
        // Get display metrics
        val displayMetrics = context.resources.displayMetrics

        // Get widget's minimum dimensions (in dp, need to convert to px)
        val minWidthDp = widgetInfo.minWidth
        val minHeightDp = widgetInfo.minHeight

        // Convert dp to pixels
        val minWidthPx = (minWidthDp * displayMetrics.density).toInt()
        val minHeightPx = (minHeightDp * displayMetrics.density).toInt()

        // Get container dimensions
        val containerWidth = if (container.width > 0) container.width else displayMetrics.widthPixels

        // Cap widget height to a reasonable maximum to allow multiple widgets
        // Use the widget's minimum height, but cap it to avoid taking full screen
        val maxWidgetHeight = displayMetrics.heightPixels / 2 // Max 50% of screen height per widget

        // Calculate actual dimensions
        // Width always matches parent (will be set in layout params)
        val width = containerWidth - (32 * displayMetrics.density).toInt() // Leave some margin

        // Height uses the widget's minimum, but capped at reasonable maximum
        val height = when {
            minHeightPx > 0 -> minOf(minHeightPx, maxWidgetHeight)
            else -> (200 * displayMetrics.density).toInt() // Default 200dp if no minimum specified
        }

        Log.d(TAG, "Widget size calculation: minW=${minWidthDp}dp, minH=${minHeightDp}dp -> ${width}px x ${height}px")

        return WidgetDimensions(
            width = width,
            height = height,
            minWidth = minWidthPx,
            minHeight = minHeightPx
        )
    }

    /**
     * Update the size constraints for a widget
     */
    private fun updateWidgetSize(
        widgetView: AppWidgetHostView,
        widgetId: Int,
        widgetInfo: AppWidgetProviderInfo
    ) {
        val width = widgetView.width
        val height = widgetView.height

        if (width > 0 && height > 0) {
            // Calculate size in dp
            val displayMetrics = context.resources.displayMetrics
            val widthDp = (width / displayMetrics.density).toInt()
            val heightDp = (height / displayMetrics.density).toInt()

            Log.d(TAG, "Updating widget size for ID $widgetId: ${width}px x ${height}px (${widthDp}dp x ${heightDp}dp)")

            try {
                // Update the app widget size with min/max bounds
                widgetView.updateAppWidgetSize(
                    null,
                    widthDp, heightDp,  // min width, min height
                    widthDp, heightDp   // max width, max height
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget size", e)
            }
        }
    }

    private data class WidgetDimensions(
        val width: Int,
        val height: Int,
        val minWidth: Int,
        val minHeight: Int
    )

    /**
     * Ensure the widget host is listening for updates
     */
    /**
     * Ensure the AppWidgetHost is listening for updates (public)
     */
    fun ensureListening() {
        try {
            appWidgetHost.startListening()
            Log.d(TAG, "AppWidgetHost is now listening for updates")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring host listening", e)
        }
    }

    /**
     * Private version for internal use
     */
    private fun ensureHostListening() {
        ensureListening()
    }

    /**
     * Remove a widget from the container
     * Note: This also deletes the widget ID from the host
     */
    fun removeWidget(widgetId: Int, container: ViewGroup) {
        val widgetView = activeWidgets[widgetId]
        if (widgetView != null) {
            container.removeView(widgetView)
            activeWidgets.remove(widgetId)
            appWidgetHost.deleteAppWidgetId(widgetId)
            Log.d(TAG, "Widget removed: $widgetId")
        } else {
            // Widget view not found in active widgets, but still try to delete the ID
            Log.w(TAG, "Widget view not found for ID $widgetId, deleting ID anyway")
            try {
                appWidgetHost.deleteAppWidgetId(widgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete widget ID: $widgetId", e)
            }
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

        // First, notify the AppWidgetManager to send fresh RemoteViews to the host
        try {
            val widgetIds = activeWidgets.keys.toIntArray()
            if (widgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, 0)
                Log.d(TAG, "Notified AppWidgetManager to refresh ${widgetIds.size} widgets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify AppWidgetManager", e)
        }

        // Then request each view to update with latest RemoteViews
        for ((widgetId, widgetView) in activeWidgets) {
            try {
                widgetView.updateAppWidget(null)
                Log.d(TAG, "Updated widget view ID: $widgetId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget view ID: $widgetId", e)
            }
        }
    }

    /**
     * Resume listening for updates (call in onResume)
     */
    fun onResume() {
        try {
            appWidgetHost.startListening()
            // Don't call updateAllWidgets() - let widgets update naturally
            Log.d(TAG, "Resumed listening for widget updates (widgets will auto-update)")
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
     * Clear active widget views without destroying the host
     * Use this when recreating the overlay view but keeping widgets alive
     */
    fun clearActiveViews() {
        Log.d(TAG, "Clearing ${activeWidgets.size} active widget views (host stays alive)")
        activeWidgets.clear()
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
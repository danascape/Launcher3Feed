package com.prauga.pvotfeed.widget.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data store for persisting widget information
 * Saves and restores widget IDs and their configurations
 */
class WidgetDataStore(context: Context) {

    companion object {
        private const val TAG = "WidgetDataStore"
        private const val PREF_NAME = "widget_data"
        private const val KEY_WIDGET_LIST = "widget_list"
        private const val KEY_WIDGET_POSITIONS = "widget_positions"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Widget data model
     */
    data class WidgetInfo(
        val widgetId: Int,
        val packageName: String,
        val className: String,
        val label: String,
        val position: Int = 0,
        val width: Int = -1,
        val height: Int = -1
    )

    /**
     * Save widget information
     */
    fun saveWidget(widgetInfo: WidgetInfo) {
        val widgets = loadAllWidgets().toMutableList()

        // Remove if already exists (update case)
        widgets.removeAll { it.widgetId == widgetInfo.widgetId }

        // Add the new/updated widget
        widgets.add(widgetInfo)

        saveAllWidgets(widgets)
        Log.d(TAG, "Widget saved: ${widgetInfo.label} (ID: ${widgetInfo.widgetId})")
    }

    /**
     * Save multiple widgets at once
     */
    private fun saveAllWidgets(widgets: List<WidgetInfo>) {
        val jsonArray = JSONArray()

        widgets.forEach { widget ->
            val jsonObject = JSONObject().apply {
                put("widgetId", widget.widgetId)
                put("packageName", widget.packageName)
                put("className", widget.className)
                put("label", widget.label)
                put("position", widget.position)
                put("width", widget.width)
                put("height", widget.height)
            }
            jsonArray.put(jsonObject)
        }

        prefs.edit().putString(KEY_WIDGET_LIST, jsonArray.toString()).apply()
    }

    /**
     * Load all saved widgets
     */
    fun loadAllWidgets(): List<WidgetInfo> {
        val jsonString = prefs.getString(KEY_WIDGET_LIST, null) ?: return emptyList()

        return try {
            val widgets = mutableListOf<WidgetInfo>()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val widget = WidgetInfo(
                    widgetId = jsonObject.getInt("widgetId"),
                    packageName = jsonObject.getString("packageName"),
                    className = jsonObject.getString("className"),
                    label = jsonObject.getString("label"),
                    position = jsonObject.optInt("position", 0),
                    width = jsonObject.optInt("width", -1),
                    height = jsonObject.optInt("height", -1)
                )
                widgets.add(widget)
            }

            Log.d(TAG, "Loaded ${widgets.size} widgets from storage")
            widgets.sortedBy { it.position }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load widgets", e)
            emptyList()
        }
    }

    /**
     * Remove a widget
     */
    fun removeWidget(widgetId: Int) {
        val widgets = loadAllWidgets().toMutableList()
        widgets.removeAll { it.widgetId == widgetId }
        saveAllWidgets(widgets)
        Log.d(TAG, "Widget removed: ID $widgetId")
    }

    /**
     * Update widget position
     */
    fun updateWidgetPosition(widgetId: Int, newPosition: Int) {
        val widgets = loadAllWidgets().toMutableList()
        val index = widgets.indexOfFirst { it.widgetId == widgetId }

        if (index != -1) {
            val widget = widgets[index]
            widgets[index] = widget.copy(position = newPosition)
            saveAllWidgets(widgets)
            Log.d(TAG, "Widget position updated: ID $widgetId, Position $newPosition")
        }
    }

    /**
     * Update widget size
     */
    fun updateWidgetSize(widgetId: Int, width: Int, height: Int) {
        val widgets = loadAllWidgets().toMutableList()
        val index = widgets.indexOfFirst { it.widgetId == widgetId }

        if (index != -1) {
            val widget = widgets[index]
            widgets[index] = widget.copy(width = width, height = height)
            saveAllWidgets(widgets)
            Log.d(TAG, "Widget size updated: ID $widgetId, Size ${width}x${height}")
        }
    }

    /**
     * Get a specific widget
     */
    fun getWidget(widgetId: Int): WidgetInfo? {
        return loadAllWidgets().find { it.widgetId == widgetId }
    }

    /**
     * Check if a widget exists
     */
    fun hasWidget(widgetId: Int): Boolean {
        return loadAllWidgets().any { it.widgetId == widgetId }
    }

    /**
     * Get widget count
     */
    fun getWidgetCount(): Int {
        return loadAllWidgets().size
    }

    /**
     * Clear all widgets
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All widget data cleared")
    }

    /**
     * Reorder widgets
     */
    fun reorderWidgets(widgetIds: List<Int>) {
        val widgets = loadAllWidgets().toMutableList()
        val reorderedWidgets = mutableListOf<WidgetInfo>()

        widgetIds.forEachIndexed { index, widgetId ->
            widgets.find { it.widgetId == widgetId }?.let { widget ->
                reorderedWidgets.add(widget.copy(position = index))
            }
        }

        saveAllWidgets(reorderedWidgets)
        Log.d(TAG, "Widgets reordered: ${widgetIds.size} widgets")
    }
}
package com.prauga.pvotfeed.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver to handle widget picker results
 * This provides a cleaner communication mechanism than polling SharedPreferences
 */
class WidgetResultReceiver : BroadcastReceiver() {

    interface WidgetResultListener {
        fun onWidgetSelected(widgetId: Int, widgetInfo: AppWidgetProviderInfo?)
        fun onWidgetSelectionCancelled()
    }

    companion object {
        private const val TAG = "WidgetResultReceiver"

        const val ACTION_WIDGET_SELECTED = "com.prauga.pvotfeed.WIDGET_SELECTED"
        const val ACTION_WIDGET_CANCELLED = "com.prauga.pvotfeed.WIDGET_CANCELLED"

        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_WIDGET_PROVIDER = "widget_provider"
        const val EXTRA_WIDGET_LABEL = "widget_label"

        private var listener: WidgetResultListener? = null

        fun setListener(listener: WidgetResultListener?) {
            this.listener = listener
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            ACTION_WIDGET_SELECTED -> {
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                val providerString = intent.getStringExtra(EXTRA_WIDGET_PROVIDER)
                val label = intent.getStringExtra(EXTRA_WIDGET_LABEL)

                Log.d(TAG, "Widget selected: ID=$widgetId, Label=$label")

                if (widgetId != -1) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                    listener?.onWidgetSelected(widgetId, widgetInfo)
                }
            }

            ACTION_WIDGET_CANCELLED -> {
                Log.d(TAG, "Widget selection cancelled")
                listener?.onWidgetSelectionCancelled()
            }
        }
    }
}
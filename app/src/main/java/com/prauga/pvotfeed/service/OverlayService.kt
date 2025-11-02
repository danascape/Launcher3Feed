package com.prauga.pvotfeed.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.libraries.gsa.d.a.OverlaysController
import com.prauga.pvotfeed.widget.data.WidgetDataStore
import com.prauga.pvotfeed.widget.manager.WidgetHostManager

class OverlayService(): Service() {
    private lateinit var overlaysController: OverlaysController
    private var timeTickReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "OverlayService"
        // Singleton instances that persist across configuration changes
        private var widgetHostManager: WidgetHostManager? = null
        private var widgetDataStore: WidgetDataStore? = null

        fun getWidgetHostManager(): WidgetHostManager? = widgetHostManager
        fun getWidgetDataStore(): WidgetDataStore? = widgetDataStore
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // Initialize widget management components if not already created
        if (widgetHostManager == null) {
            // Use application context for better system package access
            widgetHostManager = WidgetHostManager(applicationContext)
            Log.d(TAG, "WidgetHostManager created with application context")
        }

        if (widgetDataStore == null) {
            widgetDataStore = WidgetDataStore(applicationContext)
            Log.d(TAG, "WidgetDataStore created")
        }

        // Register broadcast receiver for time/date changes that widgets need
        registerTimeTickReceiver()

        overlaysController = ConfigurationOverlayController(this)
    }

    private fun registerTimeTickReceiver() {
        try {
            timeTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_TIME_TICK,
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED,
                        Intent.ACTION_DATE_CHANGED -> {
                            Log.d(TAG, "Time/date changed, notifying widgets: ${intent.action}")
                            // Force widget host to update all widgets
                            widgetHostManager?.updateAllWidgets()
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }

            ContextCompat.registerReceiver(
                this,
                timeTickReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "Time tick receiver registered for widget updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register time tick receiver", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        overlaysController.onDestroy()

        // Unregister time tick receiver
        timeTickReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Time tick receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister time tick receiver", e)
            }
        }
        timeTickReceiver = null

        // Clean up widget manager when service is destroyed
        widgetHostManager?.onDestroy()
        widgetHostManager = null
        widgetDataStore = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return overlaysController.onBind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        this.overlaysController.onUnbind(intent)
        return false
    }
}
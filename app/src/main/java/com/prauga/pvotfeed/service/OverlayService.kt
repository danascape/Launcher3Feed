package com.prauga.pvotfeed.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.libraries.gsa.d.a.OverlaysController
import com.prauga.pvotfeed.widget.data.WidgetDataStore
import com.prauga.pvotfeed.widget.manager.WidgetHostManager

class OverlayService(): Service() {
    private lateinit var overlaysController: OverlaysController

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
            widgetHostManager = WidgetHostManager(applicationContext)
            Log.d(TAG, "WidgetHostManager created")
        }

        if (widgetDataStore == null) {
            widgetDataStore = WidgetDataStore(applicationContext)
            Log.d(TAG, "WidgetDataStore created")
        }

        overlaysController = ConfigurationOverlayController(this)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        overlaysController.onDestroy()

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
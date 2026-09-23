package dev.danascape.launcher3feed.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.google.android.libraries.gsa.d.a.OverlaysController

abstract class FeedOverlayService : Service() {
    private lateinit var overlaysController: OverlaysController

    abstract fun createOverlay(context: Context): FeedOverlay

    override fun onCreate() {
        super.onCreate()
        overlaysController = ConfigurationOverlayController(this)
    }
    override fun onDestroy() {
        overlaysController.onDestroy()
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

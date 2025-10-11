package com.prauga.pvotfeed

import android.app.Application
import com.prauga.pvotfeed.service.OverlayBridge

class FeedApp : Application() {


    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private const val TAG = "PvotFeed"
        @JvmStatic
        var instance: FeedApp? = null
            private set

        val bridge = OverlayBridge()
    }
}
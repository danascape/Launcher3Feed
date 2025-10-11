package dev.danascape.launcher3feed.helloworld

import android.app.Application
import dev.danascape.launcher3feed.helloworld.service.OverlayBridge

class FeedApp : Application() {


    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private const val TAG = "Launcher3FeedHelloWorld"
        @JvmStatic
        var instance: FeedApp? = null
            private set

        val bridge = OverlayBridge()
    }
}
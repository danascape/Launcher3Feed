package dev.danascape.launcher3feed.helloworld

import android.content.Context
import dev.danascape.launcher3feed.core.FeedOverlay
import dev.danascape.launcher3feed.core.FeedOverlayService

class HelloWorldOverlayService : FeedOverlayService() {

    override fun createOverlay(context: Context): FeedOverlay = HelloWorldOverlay(context)
}

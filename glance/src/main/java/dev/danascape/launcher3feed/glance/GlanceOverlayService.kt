package dev.danascape.launcher3feed.glance

import android.content.Context
import dev.danascape.launcher3feed.core.FeedOverlay
import dev.danascape.launcher3feed.core.FeedOverlayService

class GlanceOverlayService : FeedOverlayService() {

    override fun createOverlay(context: Context): FeedOverlay = GlanceOverlay(context)
}

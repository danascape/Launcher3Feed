package dev.danascape.launcher3feed.core

import android.content.res.Configuration
import com.google.android.libraries.gsa.d.a.OverlayController
import com.google.android.libraries.gsa.d.a.OverlaysController

internal class ConfigurationOverlayController(private val service: FeedOverlayService) : OverlaysController(service) {

    override fun createController(
        configuration: Configuration?,
        serverVersion: Int,
        clientVersion: Int
    ): OverlayController {
        val context = if (configuration != null) service.createConfigurationContext(configuration) else service
        return service.createOverlay(context)
    }
}

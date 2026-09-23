package dev.danascape.launcher3feed.helloworld

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.danascape.launcher3feed.core.FeedOverlay

class HelloWorldOverlay(context: Context) : FeedOverlay(context) {

    override fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.overlay_layout, container, false)
    }
}

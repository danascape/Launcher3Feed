package dev.danascape.launcher3feed.glance

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.widget.RemoteViews

/**
 * Host view for a widget that SystemUI owns.
 *
 * We never allocate the app widget id; SystemUI streams us the [RemoteViews] and this renders them.
 * Two things have to be handled that a normal `AppWidgetHost` would do for us:
 *
 *  - **Applying after layout.** Widgets built with Jetpack Glance render nothing if their
 *    `RemoteViews` are applied before the parent has been laid out (b/387938328). SystemUI's own
 *    `CommunalAppWidgetHostView` defers for the same reason. Without this, Compose-based widgets
 *    silently come up blank while classic RemoteViews widgets look fine.
 *  - **Size selection.** A responsive widget ships several `RemoteViews` keyed by size and
 *    `AppWidgetHostView` picks one from its own measured size, so the view must have a real size
 *    before the update is applied.
 */
class GlanceWidgetHostView(context: Context) :
    AppWidgetHostView(context), View.OnLayoutChangeListener {

    private var pendingRemoteViews: RemoteViews? = null
    private var awaitingLayout = false

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        // The hub draws widgets edge to edge; the default provider padding would double up.
        setPadding(0, 0, 0, 0)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        pendingRemoteViews = remoteViews

        if (isLaidOut && width > 0 && height > 0) {
            applyPending()
            return
        }

        if (!awaitingLayout) {
            awaitingLayout = true
            addOnLayoutChangeListener(this)
            requestLayout()
        }
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        if (right - left <= 0 || bottom - top <= 0) return
        stopAwaitingLayout()
        applyPending()
    }

    private fun applyPending() {
        val views = pendingRemoteViews ?: return
        pendingRemoteViews = null
        super.updateAppWidget(views)
    }

    private fun stopAwaitingLayout() {
        if (awaitingLayout) {
            removeOnLayoutChangeListener(this)
            awaitingLayout = false
        }
    }

    override fun onDetachedFromWindow() {
        stopAwaitingLayout()
        super.onDetachedFromWindow()
    }
}

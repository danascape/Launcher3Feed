package dev.danascape.launcher3feed.glance

import android.appwidget.AppWidgetEvent
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.os.UserHandle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.widget.RemoteViews
import com.android.systemui.communal.shared.model.CommunalWidgetContentModel
import com.android.systemui.communal.widgets.IGlanceableHubWidgetManagerService

/**
 * Reads the Glanceable Hub's widgets from SystemUI and mirrors them here.
 *
 * SystemUI keeps hosting the widgets; it streams their [RemoteViews] over binder, so these are the
 * same widget instances the lock screen shows rather than copies of them. Nothing here allocates an
 * app widget id or needs an `AppWidgetHost`.
 *
 * Binding requires the signature-level `MANAGE_GLANCEABLE_HUB_WIDGETS` permission.
 *
 * All [Callback] methods are delivered on the main thread.
 */
class GlanceableHubWidgetClient(private val context: Context) {

    interface Callback {
        /** The set of widgets on the hub, in hub order, changed. */
        fun onWidgetsUpdated(widgets: List<CommunalWidgetContentModel>)

        /** New content for [appWidgetId]. Null means the widget has nothing to show. */
        fun onWidgetViewsUpdated(appWidgetId: Int, views: RemoteViews?)

        /** The provider backing [appWidgetId] changed, e.g. it was updated or uninstalled. */
        fun onProviderInfoUpdated(appWidgetId: Int, info: AppWidgetProviderInfo?)

        /**
         * Whether the hub is reachable. It goes away around user switches and is only offered to
         * the main user, so this can flip at any time.
         */
        fun onAvailabilityChanged(available: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var callback: Callback? = null

    /**
     * Runs a widget's configuration activity, if anything can. Set by a caller that has an
     * Activity to start the `IntentSender` from; without one, configurable widgets cannot be
     * added and are reported as failed so SystemUI rolls them back.
     */
    var configurationHost: ((appWidgetId: Int, onResult: (Boolean) -> Unit) -> Unit)? = null
    private var service: IGlanceableHubWidgetManagerService? = null
    private var bound = false

    /** Widget ids we have asked the service to stream to us. */
    private val listenedWidgetIds = mutableSetOf<Int>()

    private val widgetsListener =
        object : IGlanceableHubWidgetManagerService.IGlanceableHubWidgetsListener.Stub() {
            override fun onWidgetsUpdated(widgets: MutableList<CommunalWidgetContentModel>?) {
                val snapshot = widgets?.toList() ?: emptyList()
                mainHandler.post {
                    syncWidgetListeners(snapshot)
                    callback?.onWidgetsUpdated(snapshot)
                }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val bound = IGlanceableHubWidgetManagerService.Stub.asInterface(binder)
                service = bound
                try {
                    bound.addWidgetsListener(widgetsListener)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to register widgets listener", e)
                }
                mainHandler.post { callback?.onAvailabilityChanged(true) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                listenedWidgetIds.clear()
                mainHandler.post { callback?.onAvailabilityChanged(false) }
            }
        }

    fun connect(callback: Callback) {
        this.callback = callback
        if (bound) return

        val intent = Intent().setComponent(ComponentName(SYSTEMUI_PACKAGE, SERVICE_CLASS))
        bound =
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                // Missing MANAGE_GLANCEABLE_HUB_WIDGETS, or the service is not exported on
                // this build.
                Log.e(TAG, "Not allowed to bind the Glanceable Hub widget service", e)
                false
            }

        if (!bound) {
            Log.w(TAG, "Could not bind $SYSTEMUI_PACKAGE/$SERVICE_CLASS")
            mainHandler.post { callback.onAvailabilityChanged(false) }
        }
    }

    fun disconnect() {
        if (!bound) return

        service?.let { remote ->
            try {
                listenedWidgetIds.forEach(remote::removeAppWidgetHostListener)
                remote.removeWidgetsListener(widgetsListener)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to unregister listeners", e)
            }
        }

        listenedWidgetIds.clear()
        service = null
        callback = null

        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service was already unbound", e)
        }
        bound = false
    }

    /**
     * Removes a widget from the hub. It disappears from the lock screen too — there is one hub,
     * and this is it.
     */
    fun deleteWidget(appWidgetId: Int) {
        val remote = service ?: return
        try {
            remote.deleteWidget(appWidgetId)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to delete widget $appWidgetId", e)
        }
    }

    /**
     * Adds [provider] to the hub at [rank].
     *
     * If the widget has a configuration activity, SystemUI calls back asking us to run it,
     * which is handled by [configurationHost] when one is set.
     */
    fun addWidget(provider: ComponentName, user: UserHandle, rank: Int) {
        val remote = service ?: return
        try {
            remote.addWidget(provider, user, rank, configureCallback)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to add widget $provider", e)
        }
    }

    /** Reorders the hub. [orderedIds] is the new order, first to last. */
    fun setWidgetOrder(orderedIds: List<Int>) {
        val remote = service ?: return
        try {
            remote.updateWidgetOrder(orderedIds.toIntArray(), IntArray(orderedIds.size) { it })
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to reorder widgets", e)
        }
    }

    /** Changes how many hub rows [appWidgetId] spans. */
    fun resizeWidget(appWidgetId: Int, spanY: Int, orderedIds: List<Int>) {
        val remote = service ?: return
        try {
            remote.resizeWidget(
                appWidgetId,
                spanY,
                orderedIds.toIntArray(),
                IntArray(orderedIds.size) { it },
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to resize widget $appWidgetId", e)
        }
    }

    private val configureCallback =
        object : IGlanceableHubWidgetManagerService.IConfigureWidgetCallback.Stub() {
            override fun onConfigureWidget(
                appWidgetId: Int,
                resultReceiver:
                    IGlanceableHubWidgetManagerService.IConfigureWidgetCallback.IResultReceiver?,
            ) {
                val host = configurationHost
                if (host == null) {
                    Log.w(TAG, "Widget $appWidgetId needs configuration and nothing can run it")
                    report(resultReceiver, false)
                    return
                }
                // SystemUI is awaiting the result on a binder thread; the configuration activity
                // has to be started from the main thread.
                mainHandler.post { host(appWidgetId) { ok -> report(resultReceiver, ok) } }
            }

            private fun report(
                resultReceiver:
                    IGlanceableHubWidgetManagerService.IConfigureWidgetCallback.IResultReceiver?,
                success: Boolean,
            ) {
                try {
                    resultReceiver?.onResult(success)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to report configuration result", e)
                }
            }
        }

    /** The configuration activity for [appWidgetId], or null if it has none or cannot be reached. */
    fun configureIntentSender(appWidgetId: Int): IntentSender? {
        val remote = service ?: return null
        return try {
            remote.getIntentSenderForConfigureActivity(appWidgetId)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get configuration IntentSender for $appWidgetId", e)
            null
        }
    }

    /** Moves the hub's widget order into the same order here, dropping listeners we no longer need. */
    private fun syncWidgetListeners(widgets: List<CommunalWidgetContentModel>) {
        val remote = service ?: return
        val wanted = widgets.map { it.appWidgetId }.toSet()

        (listenedWidgetIds - wanted).forEach { appWidgetId ->
            try {
                remote.removeAppWidgetHostListener(appWidgetId)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to drop listener for $appWidgetId", e)
            }
            listenedWidgetIds.remove(appWidgetId)
        }

        (wanted - listenedWidgetIds).forEach { appWidgetId ->
            try {
                remote.setAppWidgetHostListener(appWidgetId, hostListenerFor(appWidgetId))
                listenedWidgetIds.add(appWidgetId)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to listen to $appWidgetId", e)
            }
        }
    }

    private fun hostListenerFor(appWidgetId: Int) =
        object : IGlanceableHubWidgetManagerService.IAppWidgetHostListener.Stub() {

            override fun onUpdateProviderInfo(appWidget: AppWidgetProviderInfo?) {
                mainHandler.post { callback?.onProviderInfoUpdated(appWidgetId, appWidget) }
            }

            override fun updateAppWidget(views: RemoteViews?) {
                mainHandler.post { callback?.onWidgetViewsUpdated(appWidgetId, views) }
            }

            override fun updateAppWidgetDeferred(packageName: String?, appWidgetId: Int) {
                // The RemoteViews were too large to pass over binder. A real AppWidgetHost would
                // fetch them itself, which we cannot do because SystemUI owns this widget id.
                // The next full update from the provider will come through normally.
                Log.w(TAG, "Deferred update for $appWidgetId from $packageName; skipping")
            }

            override fun onViewDataChanged(viewId: Int) {
                // Collection views (ListView, GridView) are not rendered here yet.
            }

            // Called on a binder thread on purpose; it must not hop to the main thread.
            override fun collectWidgetEvent(): AppWidgetEvent? = null
        }

    companion object {
        private const val TAG = "GlanceHubClient"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val SERVICE_CLASS =
            "com.android.systemui.communal.widgets.GlanceableHubWidgetManagerService"
    }
}

package com.prauga.pvotfeed.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log

/**
 * Widget picker implementation inspired by SystemUI's approach.
 * This uses the system's launcher to provide the widget picker UI.
 */
class SystemUIStyleWidgetPicker(private val context: Context) {

    companion object {
        private const val TAG = "SystemUIStyleWidgetPicker"

        // Intent extras used by launchers for widget picker
        private const val EXTRA_DESIRED_WIDGET_WIDTH = "desired_widget_width"
        private const val EXTRA_DESIRED_WIDGET_HEIGHT = "desired_widget_height"
        private const val EXTRA_PICKER_TITLE = "picker_title"
        private const val EXTRA_PICKER_DESCRIPTION = "picker_description"
        private const val EXTRA_UI_SURFACE_KEY = "ui_surface"
        private const val EXTRA_UI_SURFACE_VALUE = "pvot_feed"
        private const val EXTRA_USER_ID_FILTER = "filtered_user_ids"
        private const val EXTRA_ADDED_APP_WIDGETS_KEY = "added_app_widgets"

        // Result extras
        private const val EXTRA_IS_PENDING_WIDGET_DRAG = "is_pending_widget_drag"

        // Widget categories we want to show
        private val WIDGET_CATEGORIES = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    }

    private val packageManager: PackageManager = context.packageManager

    /**
     * Get the default launcher package name
     */
    private fun getLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolveInfo = packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Create an intent to launch the widget picker via the launcher
     *
     * @param excludedWidgets List of already added widgets to exclude from picker
     * @param title Title for the picker (optional)
     * @param description Description for the picker (optional)
     */
    fun createWidgetPickerIntent(
        excludedWidgets: List<AppWidgetProviderInfo> = emptyList(),
        title: String = "Add Widget",
        description: String = "Choose a widget to add to your feed",
        desiredWidth: Int = 400,
        desiredHeight: Int = 300
    ): Intent? {
        val launcherPackage = getLauncherPackage()

        if (launcherPackage == null) {
            Log.e(TAG, "No launcher package found")
            return null
        }

        Log.d(TAG, "Using launcher package: $launcherPackage")

        return Intent(Intent.ACTION_PICK).apply {
            setPackage(launcherPackage)

            // Set desired widget dimensions
            putExtra(EXTRA_DESIRED_WIDGET_WIDTH, desiredWidth)
            putExtra(EXTRA_DESIRED_WIDGET_HEIGHT, desiredHeight)

            // Set widget category filter
            // Note: EXTRA_CATEGORY_FILTER is available in API 21+
            putExtra(
                "appWidgetCategory",  // AppWidgetManager.EXTRA_CATEGORY_FILTER value
                WIDGET_CATEGORIES
            )

            // UI customization
            putExtra(EXTRA_UI_SURFACE_KEY, EXTRA_UI_SURFACE_VALUE)
            putExtra(EXTRA_PICKER_TITLE, title)
            putExtra(EXTRA_PICKER_DESCRIPTION, description)

            // Exclude already added widgets
            if (excludedWidgets.isNotEmpty()) {
                putParcelableArrayListExtra(
                    EXTRA_ADDED_APP_WIDGETS_KEY,
                    ArrayList(excludedWidgets)
                )
            }
        }
    }

    /**
     * Parse the result from the widget picker
     */
    fun parsePickerResult(data: Intent?): WidgetPickerResult? {
        if (data == null) {
            Log.w(TAG, "No data in picker result")
            return null
        }

        // Check if this is a drag & drop operation
        val isPendingDrag = data.getBooleanExtra(EXTRA_IS_PENDING_WIDGET_DRAG, false)
        if (isPendingDrag) {
            Log.d(TAG, "Widget is being dragged, no immediate action needed")
            return WidgetPickerResult(isDragOperation = true)
        }

        // Extract ComponentName and UserHandle
        val componentName = data.getParcelableExtra(
            Intent.EXTRA_COMPONENT_NAME,
            ComponentName::class.java
        )

        val user = data.getParcelableExtra(
            Intent.EXTRA_USER,
            UserHandle::class.java
        )

        if (componentName != null && user != null) {
            Log.d(TAG, "Widget selected: $componentName for user: $user")
            return WidgetPickerResult(
                componentName = componentName,
                user = user,
                isDragOperation = false
            )
        }

        Log.w(TAG, "No ComponentName or UserHandle found in result")
        return null
    }

    /**
     * Result from the widget picker
     */
    data class WidgetPickerResult(
        val componentName: ComponentName? = null,
        val user: UserHandle? = null,
        val isDragOperation: Boolean = false
    )

    /**
     * Check if the device has a launcher that supports widget picking
     */
    fun isWidgetPickerSupported(): Boolean {
        val launcherPackage = getLauncherPackage() ?: return false

        val intent = Intent(Intent.ACTION_PICK).apply {
            setPackage(launcherPackage)
        }

        val activities = packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }

    /**
     * Alternative: Use the standard AppWidget picker if launcher doesn't support ACTION_PICK
     */
    fun createStandardWidgetPickerIntent(
        widgetId: Int
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

            // Optional: Add custom widgets to the picker
            // val customInfo = ArrayList<AppWidgetProviderInfo>()
            // val customExtras = ArrayList<Bundle>()
            // putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo)
            // putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras)
        }
    }
}
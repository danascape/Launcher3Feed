package com.prauga.pvotfeed.service

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.libraries.gsa.d.a.OverlayController
import com.prauga.pvotfeed.FeedApp
import com.prauga.pvotfeed.R
import com.prauga.pvotfeed.widget.data.WidgetDataStore
import com.prauga.pvotfeed.widget.helper.WidgetPickerHelper
import com.prauga.pvotfeed.widget.manager.WidgetHostManager
import com.prauga.pvotfeed.widget.view.WidgetContainerView
import com.prauga.pvotfeed.widget.view.WidgetPickerView
import com.prauga.pvotfeed.widget.WidgetResultReceiver
import com.prauga.pvotfeed.widget.WidgetPickerActivity

class OverlayView(private val context: Context) : OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    OverlayBridge.OverlayBridgeCallback,
    WidgetResultReceiver.WidgetResultListener {

    private lateinit var rootView: View
    private lateinit var btnEdit: Button
    private lateinit var btnDone: Button
    private lateinit var widgetContainer: WidgetContainerView
    private lateinit var widgetPickerView: WidgetPickerView

    // Widget management components
    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var widgetPickerHelper: WidgetPickerHelper
    private lateinit var widgetDataStore: WidgetDataStore
    private lateinit var widgetResultReceiver: WidgetResultReceiver

    private var isEditMode: Boolean = false
    private lateinit var gestureDetector: GestureDetector

    companion object {
        private const val TAG = "OverlayView"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing OverlayView")

        if (container != null) {
            // Use simpler layout for testing
            rootView = View.inflate(context, R.layout.overlay_layout_simple, null)
            container.addView(rootView)
            Log.d(TAG, "Content view added to container")

            // Debug: Check if rootView is properly initialized
            Log.d(TAG, "RootView class: ${rootView.javaClass.simpleName}")
            Log.d(TAG, "RootView clickable: ${rootView.isClickable}")
            Log.d(TAG, "RootView longClickable: ${rootView.isLongClickable}")
        } else {
            Log.e(TAG, "Container is null!")
        }

        // Initialize gesture detector
        initGestureDetector()

        // Initialize headers first
        initEditHeader()

        // Initialize widget components
        initializeWidgetSystem()

        // Setup touch listeners after all views are initialized
        setupTouchListeners()

        // Register callback
        FeedApp.bridge.setCallback(this)

        // Try to intercept touch events on the window
        setupWindowTouchInterception()

        Log.d(TAG, "OverlayView created successfully")
    }

    private fun initializeWidgetSystem() {
        try {
            Log.d(TAG, "Initializing widget system...")

            // Get shared widget managers from service (they persist across configuration changes)
            val sharedHostManager = OverlayService.getWidgetHostManager()
            val sharedDataStore = OverlayService.getWidgetDataStore()

            if (sharedHostManager != null && sharedDataStore != null) {
                widgetHostManager = sharedHostManager
                widgetDataStore = sharedDataStore
                Log.d(TAG, "Using shared widget managers from service")
            } else {
                // Fallback: create local instances (shouldn't happen if service is running)
                Log.w(TAG, "Service managers not available, creating local instances")
                widgetHostManager = WidgetHostManager(context)
                widgetDataStore = WidgetDataStore(context)
            }

            widgetPickerHelper = WidgetPickerHelper(context)
            Log.d(TAG, "Widget managers initialized")

            // Set up broadcast receiver for widget picker results
            try {
                widgetResultReceiver = WidgetResultReceiver()
                WidgetResultReceiver.setListener(this)

                // Register the broadcast receiver
                val intentFilter = IntentFilter().apply {
                    addAction(WidgetResultReceiver.ACTION_WIDGET_SELECTED)
                    addAction(WidgetResultReceiver.ACTION_WIDGET_CANCELLED)
                }
                ContextCompat.registerReceiver(
                    context,
                    widgetResultReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                Log.d(TAG, "Broadcast receiver registered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set up broadcast receiver", e)
                // Continue without broadcast receiver - fallback to custom picker
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing widget managers", e)
            // Initialize at least the basic managers to prevent NPE
            widgetHostManager = WidgetHostManager(context)
            widgetDataStore = WidgetDataStore(context)
        }

        // Find or create widget container
        widgetContainer = rootView.findViewById(R.id.widget_container)
            ?: WidgetContainerView(context).also { container ->
                // If not in layout, add it programmatically
                val parentView = rootView as? android.view.ViewGroup
                parentView?.addView(container)
            }

        // Initialize widget picker view
        try {
            widgetPickerView = rootView.findViewById(R.id.widgetPickerView)
            if (widgetPickerView == null) {
                Log.e(TAG, "Widget picker view not found in layout, creating programmatically")
                widgetPickerView = WidgetPickerView(context).also { picker ->
                    // If not in layout, add it programmatically
                    val parentView = rootView as? android.view.ViewGroup
                    parentView?.addView(picker)
                }
            } else {
                Log.d(TAG, "Widget picker view found in layout")
            }

            // Set up widget picker listeners
            widgetPickerView.setOnWidgetSelectedListener { widgetInfo ->
                Log.d(TAG, "Widget selected from picker: ${widgetInfo.label}")
                Log.d(TAG, "Provider: ${widgetInfo.provider}")
                addWidgetDirectly(widgetInfo)
            }
            Log.d(TAG, "Widget selection listener set on picker view")

            widgetPickerView.setOnCloseListener {
                widgetPickerView.hide()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize widget picker view", e)
            // Create a minimal picker view to prevent NPE
            widgetPickerView = WidgetPickerView(context)
        }

        // Check for first launch
        val isFirstLaunch = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            .getBoolean("first_launch", true)

        if (isFirstLaunch) {
            addDefaultWidgets()

            // Mark first launch as complete
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("first_launch", false)
                .apply()
        } else {
            // Restore previously saved widgets
            try {
                restoreSavedWidgets()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore saved widgets", e)
            }
        }

        Log.d(TAG, "Widget system initialized")
    }

    private fun addDefaultWidgets() {
        // List of default widgets to add
        val defaultWidgets = listOf(
            // Cromite search widget
            ComponentName(
                "org.cromite.cromite",
                "org.chromium.chrome.browser.quickactionsearchwidget.QuickActionSearchWidgetProvider\$QuickActionSearchWidgetProviderSearch"
            )
        )

        defaultWidgets.forEach { componentName ->
            try {
                addWidgetByComponentName(componentName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add default widget: $componentName", e)
            }
        }
    }

    private fun addWidgetByComponentName(componentName: ComponentName) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Get all installed widgets
            val installedProviders = appWidgetManager.installedProviders

            // Find the widget provider matching the component name
            val providerInfo = installedProviders.firstOrNull {
                it.provider == componentName
            }

            if (providerInfo == null) {
                Log.w(TAG, "Widget provider not found: $componentName")
                Log.d(TAG, "Available providers: ${installedProviders.map { it.provider }}")
                return
            }

            // Allocate a widget ID
            val widgetId = widgetHostManager.allocateWidgetId()

            // Bind the widget
            val bindSuccess = appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId,
                componentName
            )

            if (!bindSuccess) {
                Log.e(TAG, "Failed to bind widget: ${providerInfo.label}")
                widgetHostManager.deleteWidgetId(widgetId)
                return
            }

            widgetHostManager.addWidgetToContainer(widgetId, providerInfo, widgetContainer, showToast = false)
            saveWidgetInfo(widgetId, providerInfo)
            Log.d(TAG, "Default widget added successfully: ${providerInfo.label}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding widget by component name: $componentName", e)
        }
    }

    private fun restoreSavedWidgets() {
        val savedWidgets = widgetDataStore.loadAllWidgets()
        Log.d(TAG, "Restoring ${savedWidgets.size} saved widgets")

        if (savedWidgets.isEmpty()) {
            Log.d(TAG, "No saved widgets to restore")
            return
        }

        // Clear the container and active views before restoring
        widgetContainer.removeAllViews()
        widgetHostManager.clearActiveViews()
        Log.d(TAG, "Container and active views cleared, ready for restoration")

        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Restore widgets in order of their position
        savedWidgets.sortedBy { it.position }.forEach { savedWidget ->
            try {
                Log.d(TAG, "Attempting to restore widget: ${savedWidget.label} (ID: ${savedWidget.widgetId})")

                // Get the widget provider info for this widget ID
                val widgetInfo = appWidgetManager.getAppWidgetInfo(savedWidget.widgetId)

                if (widgetInfo != null) {
                    // Widget is still valid, add it to the container
                    Log.d(TAG, "Restoring widget: ${widgetInfo.label}")
                    try {
                        widgetHostManager.addWidgetToContainer(savedWidget.widgetId, widgetInfo, widgetContainer, showToast = false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add widget during restoration: ${widgetInfo.label}", e)
                        // Don't remove from saved data - might work next time
                    }
                } else {
                    // Widget ID is no longer valid, remove it from saved data
                    Log.w(TAG, "Widget ID ${savedWidget.widgetId} is no longer valid, removing from saved data")
                    widgetDataStore.removeWidget(savedWidget.widgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore widget: ${savedWidget.label}", e)
                // Don't automatically remove - the widget might work later
            }
        }

        Log.d(TAG, "Widget restoration complete")
    }

    private fun setupTouchListeners() {
        // Get references to all interactive views
        val scrollView = rootView.findViewById<View>(R.id.scrollView)

        // Setup long click listener for multiple views
        val longClickListener = View.OnLongClickListener { v ->
            Log.d(TAG, "Long click detected on ${v.javaClass.simpleName}, isEditMode: $isEditMode")
            if (!isEditMode) {
                toggleEditButtons(true)
                Toast.makeText(context, "Edit mode enabled", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        // Setup click listener for multiple views
        val clickListener = View.OnClickListener { v ->
            Log.d(TAG, "Click detected on ${v.javaClass.simpleName}, isEditMode: $isEditMode")
            if (isEditMode) {
                toggleEditButtons(false)
                Toast.makeText(context, "Edit mode disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Apply listeners to the root view first
        rootView.setOnLongClickListener(longClickListener)
        rootView.setOnClickListener(clickListener)
        rootView.isLongClickable = true
        rootView.isClickable = true

        // Also add to scrollView if it exists
        scrollView?.let { sv ->
            sv.setOnLongClickListener(longClickListener)
            sv.setOnClickListener(clickListener)
            sv.isLongClickable = true
            sv.isClickable = true
        }

        // Also add to widget container if initialized
        if (::widgetContainer.isInitialized) {
            widgetContainer.setOnLongClickListener(longClickListener)
            widgetContainer.setOnClickListener(clickListener)
            widgetContainer.isLongClickable = true
            widgetContainer.isClickable = true
        }

        Log.d(TAG, "Touch listeners setup complete - rootView clickable: ${rootView.isClickable}, longClickable: ${rootView.isLongClickable}")
    }

    private fun initEditHeader() {
        val btnEdit = rootView.findViewById<View>(R.id.btnEdit)
        val btnDone = rootView.findViewById<View>(R.id.btnDone)

        if (btnEdit == null || btnDone == null) {
            Log.e(TAG, "Edit buttons not found in layout!")
            return
        }

        Log.d(TAG, "Edit buttons found and initializing")

        btnEdit.setOnClickListener { anchor ->
            val popupMenu = PopupMenu(this, anchor)
            popupMenu.menuInflater.inflate(R.menu.actions_edit_menu, popupMenu.menu)
            popupMenu.show()
            popupMenu.setOnMenuItemClickListener { it ->
                when(it.itemId) {
                    R.id.action_add_widget -> {
                        Log.d(TAG, "Add widget clicked")
                        launchWidgetPicker()
                        true
                    }
                    R.id.action_customise -> {
                        Log.d(TAG, "Customise clicked")
                        true
                    }
                    else -> false
                }
            }
        }

        btnDone.setOnClickListener {
            Toast.makeText(context, "Done button clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEditButtons(show: Boolean) {
        val btnEdit = rootView.findViewById<View>(R.id.btnEdit)
        val btnDone = rootView.findViewById<View>(R.id.btnDone)

        if (show) {
            // Show the buttons
            btnEdit.visibility = View.VISIBLE
            btnDone.visibility = View.VISIBLE
            isEditMode = true
        } else {
            // Hide the buttons
            btnEdit.visibility = View.GONE
            btnDone.visibility = View.GONE
            isEditMode = false
        }
    }

    override fun onScroll(progress: Float) {
        super.onScroll(progress)
        //Log.d(TAG, "onScroll: $progress")

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun launchWidgetPicker() {
        try {
            Log.d(TAG, "Attempting to launch native widget picker")

            // First try to use the native widget picker through our bridge activity
            val useNativePicker = true // Set to false to use custom picker

            if (useNativePicker) {
                launchNativeWidgetPicker()
            } else {
                // Fall back to custom picker if native picker doesn't work
                showWidgetSelectionDialog()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch widget picker", e)
            Toast.makeText(context, "Unable to open widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fall back to custom picker
            showWidgetSelectionDialog()
        }
    }

    private fun launchNativeWidgetPicker() {
        try {
            Log.d(TAG, "Launching native widget picker via WidgetPickerActivity")

            // Launch our bridge activity that will handle the native picker
            val intent = Intent(context, WidgetPickerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

            context.startActivity(intent)

            // The result will be received via broadcast receiver

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch native widget picker", e)
            Toast.makeText(context, "Falling back to custom picker", Toast.LENGTH_SHORT).show()
            showWidgetSelectionDialog()
        }
    }

    private fun showWidgetSelectionDialog() {
        try {
            Log.d(TAG, "Showing widget picker view")

            // Show the embedded widget picker view
            widgetPickerView.show()

            Log.d(TAG, "Widget picker view shown")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show widget picker view", e)
            Toast.makeText(context, "Failed to show widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addWidgetDirectly(providerInfo: android.appwidget.AppWidgetProviderInfo) {
        Log.d(TAG, "addWidgetDirectly called for: ${providerInfo.label}")
        try {
            val widgetId = widgetHostManager.allocateWidgetId()
            Log.d(TAG, "Allocated widget ID: $widgetId")

            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)

            Log.d(TAG, "Trying to bind widget: ${providerInfo.label} (${providerInfo.provider})")

            // For system apps with BIND_APPWIDGET permission
            val success = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, providerInfo.provider)

            if (success) {
                Log.d(TAG, "Widget bound successfully, adding to container")
                widgetHostManager.addWidgetToContainer(widgetId, providerInfo, widgetContainer)
                saveWidgetInfo(widgetId, providerInfo)
                Toast.makeText(context, "Added widget: ${providerInfo.label}", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Widget added to container successfully")
            } else {
                Log.w(TAG, "Failed to bind widget - permission denied")
                Toast.makeText(context, "Unable to add widget - permission required", Toast.LENGTH_SHORT).show()

                // For Android 12+, we might need to request permission differently
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Log.d(TAG, "Trying alternative binding for Android 12+")
                    // Could implement permission request here
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add widget directly", e)
            Toast.makeText(context, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryDirectWidgetAddition() {
        try {
            // For system apps, we can try to directly add common widgets
            // This is a fallback approach when the picker doesn't work

            val widgetId = widgetHostManager.allocateWidgetId()

            // Example: Try to add a clock widget directly
            val clockProvider = ComponentName("com.android.deskclock", "com.android.alarmclock.DigitalAppWidgetProvider")
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)

            val success = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, clockProvider)
            if (success) {
                val widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                if (widgetInfo != null) {
                    widgetHostManager.addWidgetToContainer(widgetId, widgetInfo, widgetContainer)
                    saveWidgetInfo(widgetId, widgetInfo)
                }
            } else {
                Log.w(TAG, "Direct widget binding not allowed")
                // For system apps, we might need to request permission or use different approach
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add widget directly", e)
        }
    }

    private fun saveWidgetInfo(widgetId: Int, widgetInfo: android.appwidget.AppWidgetProviderInfo) {
        // Widget has already been added to container, so use count - 1 for 0-indexed position
        val position = maxOf(0, widgetContainer.getWidgetCount() - 1)
        val info = WidgetDataStore.WidgetInfo(
            widgetId = widgetId,
            packageName = widgetInfo.provider.packageName,
            className = widgetInfo.provider.className,
            label = widgetInfo.loadLabel(context.packageManager),
            position = position
        )
        widgetDataStore.saveWidget(info)
        Log.d(TAG, "Saved widget info: ${info.label} at position $position")
    }

    /**
     * Remove a widget from both the container and data store
     */
    private fun removeWidgetById(widgetId: Int) {
        try {
            widgetHostManager.removeWidget(widgetId, widgetContainer)
            widgetDataStore.removeWidget(widgetId)
            Log.d(TAG, "Widget removed: ID $widgetId")
            Toast.makeText(context, "Widget removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove widget: ID $widgetId", e)
            Toast.makeText(context, "Failed to remove widget", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // NOTE: Do NOT destroy widgetHostManager here - it's owned by the Service
        // and needs to persist across configuration changes
        Log.d(TAG, "OverlayView onDestroy - keeping widget host alive for service")

        FeedApp.bridge.setCallback(null)

        // Unregister broadcast receiver if initialized
        if (::widgetResultReceiver.isInitialized) {
            try {
                context.unregisterReceiver(widgetResultReceiver)
                WidgetResultReceiver.setListener(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        Log.d(TAG, "OverlayView destroyed")
    }

    // WidgetResultListener implementation
    override fun onWidgetSelected(widgetId: Int, widgetInfo: AppWidgetProviderInfo?) {
        Log.d(TAG, "Widget selected via broadcast: ID=$widgetId")

        if (widgetInfo != null) {
            try {
                widgetHostManager.addWidgetToContainer(widgetId, widgetInfo, widgetContainer)
                saveWidgetInfo(widgetId, widgetInfo)
                Toast.makeText(context, "Widget added: ${widgetInfo.label}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add widget from broadcast", e)
                Toast.makeText(context, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Widget info is null for ID: $widgetId")
            Toast.makeText(context, "Failed to get widget information", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWidgetSelectionCancelled() {
        Log.d(TAG, "Widget selection cancelled via broadcast")
        Toast.makeText(context, "Widget selection cancelled", Toast.LENGTH_SHORT).show()
    }

    // OverlayBridge callback methods
    override fun applyNewTheme(value: String) {
        Log.d(TAG, "applyNewTheme: $value")
    }

    override fun applyCompactCard(value: Boolean) {
        Log.d(TAG, "applyCompactCard: $value")
    }

    override fun applyNewTransparency(value: Float) {
        Log.d(TAG, "applyNewTransparency: $value")

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onClientMessage(action: String) {
        Log.d(TAG, "onClientMessage: $action")
    }

    private fun setupWindowTouchInterception() {
        try {
            // Get the window and its decorView
            window?.let { win ->
                val decorView = win.decorView

                // Set a touch listener on the decor view
                decorView.setOnTouchListener { _, event ->
                    //Log.d(TAG, "Window touch event: action=${event.action}, x=${event.x}, y=${event.y}")

                    // Try to handle with gesture detector
                    if (gestureDetector.onTouchEvent(event)) {
                        return@setOnTouchListener true
                    }

                    false
                }

                Log.d(TAG, "Window touch interception setup complete")
            } ?: Log.w(TAG, "Window is null, cannot setup touch interception")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup window touch interception", e)
        }
    }

    private fun initGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                Log.d(TAG, "GestureDetector: Long press detected at (${e.x}, ${e.y})")
                if (!isEditMode) {
                    toggleEditButtons(true)
                    Toast.makeText(context, "Edit mode enabled", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                //Log.d(TAG, "GestureDetector: Single tap detected at (${e.x}, ${e.y})")
                if (isEditMode) {
                    toggleEditButtons(false)
                    Toast.makeText(context, "Edit mode disabled", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        //Log.d(TAG, "dispatchTouchEvent: action=${ev.action}, x=${ev.x}, y=${ev.y}")

        // Check if the touch is on the buttons
        if (isEditMode) {
            val btnEdit = rootView.findViewById<View>(R.id.btnEdit)
            val btnDone = rootView.findViewById<View>(R.id.btnDone)

            if (isTouchOnView(ev, btnEdit) || isTouchOnView(ev, btnDone)) {
                // Let the button handle it, don't process with gesture detector
                return super.dispatchTouchEvent(ev)
            }
        }

        // Let gesture detector handle the event
        if (gestureDetector.onTouchEvent(ev)) {
            return true
        }

        // If not handled by gesture detector, pass to parent
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchOnView(event: MotionEvent, view: View?): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        return x >= location[0] && x <= location[0] + view.width &&
               y >= location[1] && y <= location[1] + view.height
    }
}
package com.prauga.pvotfeed.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity to handle widget picker functionality
 * This activity bridges the gap between OverlayController and native widget picker
 */
class WidgetPickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WidgetPickerActivity"
        private const val WIDGET_HOST_ID = 1024

        // Request codes
        const val REQUEST_PICK_APPWIDGET = 9
        const val REQUEST_CREATE_APPWIDGET = 10
        const val REQUEST_BIND_APPWIDGET = 11

        // Result extras
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_WIDGET_INFO = "widget_info"
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize widget components
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
        appWidgetHost.startListening()

        // Start the widget picker immediately
        pickWidget()
    }

    private fun pickWidget() {
        Log.d(TAG, "=== PICK WIDGET START ===")

        // Allocate a new widget ID
        pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        Log.d(TAG, "Allocated widget ID: $pendingWidgetId")

        // Check if the host is listening
        try {
            appWidgetHost.startListening()
            Log.d(TAG, "AppWidgetHost is listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting widget host", e)
        }

        // Create the picker intent
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)

            // Optional: Add custom widgets to the picker
            // val customInfo = ArrayList<AppWidgetProviderInfo>()
            // val customExtras = ArrayList<Bundle>()
            // putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo)
            // putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras)
        }

        try {
            startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
            Log.d(TAG, "Widget picker launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch widget picker", e)
            Toast.makeText(this, "Failed to open widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanup()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_PICK_APPWIDGET -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (widgetId != -1) {
                        Log.d(TAG, "Widget selected with ID: $widgetId")
                        configureWidget(widgetId)
                    } else {
                        Log.e(TAG, "Invalid widget ID received")
                        sendCancellationBroadcast()
                        cleanup()
                        finish()
                    }
                } else {
                    Log.d(TAG, "Widget picker cancelled")
                    sendCancellationBroadcast()
                    cleanup()
                    finish()
                }
            }

            REQUEST_CREATE_APPWIDGET -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (widgetId != -1) {
                        Log.d(TAG, "Widget configured with ID: $widgetId")
                        onWidgetConfigured(widgetId)
                    } else {
                        Log.e(TAG, "Invalid widget ID from configuration")
                        cleanup()
                        finish()
                    }
                } else {
                    Log.d(TAG, "Widget configuration cancelled")
                    cleanup()
                    finish()
                }
            }

            REQUEST_BIND_APPWIDGET -> {
                Log.d(TAG, "=== BIND PERMISSION CALLBACK ===")
                Log.d(TAG, "Result code: $resultCode (OK=${Activity.RESULT_OK})")

                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Permission dialog returned OK, attempting to bind again...")
                    // Try to bind again after permission granted
                    if (pendingWidgetId != -1) {
                        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
                        if (appWidgetInfo != null) {
                            // Try with user handle first
                            var canBindNow = false
                            try {
                                canBindNow = appWidgetManager.bindAppWidgetIdIfAllowed(
                                    pendingWidgetId,
                                    Process.myUserHandle(),
                                    appWidgetInfo.provider,
                                    null
                                )
                                Log.d(TAG, "Bind with user after permission: $canBindNow")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to bind with user after permission", e)
                            }

                            // Fallback to standard binding
                            if (!canBindNow) {
                                canBindNow = appWidgetManager.bindAppWidgetIdIfAllowed(
                                    pendingWidgetId,
                                    appWidgetInfo.provider
                                )
                                Log.d(TAG, "Standard bind after permission: $canBindNow")
                            }
                            if (canBindNow) {
                                Log.d(TAG, "Widget successfully bound after permission")
                                // Check if needs configuration
                                if (appWidgetInfo.configure != null) {
                                    val configIntent = Intent().apply {
                                        component = appWidgetInfo.configure
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                                    }
                                    try {
                                        startActivityForResult(configIntent, REQUEST_CREATE_APPWIDGET)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch configuration", e)
                                        onWidgetConfigured(pendingWidgetId)
                                    }
                                } else {
                                    onWidgetConfigured(pendingWidgetId)
                                }
                            } else {
                                Log.e(TAG, "Still cannot bind widget after permission")
                                tryDirectWidgetAdd(pendingWidgetId, appWidgetInfo)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Widget binding permission denied or cancelled")
                    // Try alternative approach
                    if (pendingWidgetId != -1) {
                        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
                        if (appWidgetInfo != null) {
                            Log.d(TAG, "Attempting fallback widget add method")
                            tryDirectWidgetAdd(pendingWidgetId, appWidgetInfo)
                        } else {
                            Toast.makeText(this, "Unable to add widget without permission", Toast.LENGTH_SHORT).show()
                            cleanup()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun configureWidget(widgetId: Int) {
        Log.d(TAG, "=== CONFIGURE WIDGET START ===")
        Log.d(TAG, "Widget ID: $widgetId")

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)

        if (appWidgetInfo == null) {
            Log.e(TAG, "Failed to get widget info for ID: $widgetId")
            cleanup()
            finish()
            return
        }

        Log.d(TAG, "Widget info retrieved successfully")
        Log.d(TAG, "  Label: ${appWidgetInfo.label}")
        Log.d(TAG, "  Provider: ${appWidgetInfo.provider}")
        Log.d(TAG, "  Package: ${appWidgetInfo.provider.packageName}")
        Log.d(TAG, "  Class: ${appWidgetInfo.provider.className}")

        // Store the widget ID for later use in case permission is needed
        pendingWidgetId = widgetId

        // First check if we have the permission in general
        val hasBindPermission = checkSelfPermission(android.Manifest.permission.BIND_APPWIDGET) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "checkSelfPermission(BIND_APPWIDGET): $hasBindPermission")

        // Check if the widget is already bound
        val isAlreadyBound = appWidgetManager.getAppWidgetInfo(widgetId) != null
        Log.d(TAG, "Widget already has info (pre-bound): $isAlreadyBound")

        // Try different binding approaches
        var canBind = false

        // Method 1: Try standard binding first (this should work if permission was granted via ADB)
        Log.d(TAG, "Attempting Method 1: Standard binding...")
        try {
            canBind = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, appWidgetInfo.provider)
            Log.d(TAG, "Method 1 Result: bindAppWidgetIdIfAllowed = $canBind")

            // Double-check by trying to get the info again
            val checkInfo = appWidgetManager.getAppWidgetInfo(widgetId)
            Log.d(TAG, "After Method 1, widget info exists: ${checkInfo != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Method 1 Exception: ${e.message}", e)
        }

        // Method 2: Try with user parameter if standard failed
        if (!canBind) {
            Log.d(TAG, "Method 1 failed, attempting Method 2: Binding with user handle...")
            try {
                val userHandle = android.os.Process.myUserHandle()
                Log.d(TAG, "Using UserHandle: $userHandle")

                canBind = appWidgetManager.bindAppWidgetIdIfAllowed(
                    widgetId,
                    userHandle,
                    appWidgetInfo.provider,
                    null
                )
                Log.d(TAG, "Method 2 Result: bindAppWidgetIdIfAllowed = $canBind")

                // Double-check
                val checkInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                Log.d(TAG, "After Method 2, widget info exists: ${checkInfo != null}")
            } catch (e: Exception) {
                Log.e(TAG, "Method 2 Exception: ${e.message}", e)
            }
        }

        Log.d(TAG, "=== BINDING RESULT: canBind = $canBind ===")

        if (canBind) {
            Log.d(TAG, "SUCCESS: Widget binding succeeded!")
            // Continue with configuration or completion
            handleSuccessfulBinding(widgetId, appWidgetInfo)
        } else {
            Log.d(TAG, "FAILURE: Cannot bind widget after all attempts")
            Log.d(TAG, "Will attempt to request explicit permission...")

            // Don't immediately show "limited functionality" - first try requesting permission
            val provider = appWidgetInfo.provider

            // Request permission to bind the widget
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            }

            try {
                Log.d(TAG, "Requesting bind permission for widget ID: $widgetId, provider: $provider")
                startActivityForResult(bindIntent, REQUEST_BIND_APPWIDGET)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request bind permission: ${e.message}", e)

                // Only show limited functionality if we really can't request permission
                if (e.message?.contains("No Activity found") == true ||
                    e.message?.contains("Permission Denial") == true) {
                    Log.d(TAG, "System doesn't support permission request, attempting direct add")
                    tryDirectWidgetAdd(widgetId, appWidgetInfo)
                } else {
                    Toast.makeText(this, "Error requesting permission: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanup()
                    finish()
                }
            }
        }
    }

    private fun handleSuccessfulBinding(widgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        Log.d(TAG, "Handling successful binding for widget: ${appWidgetInfo.label}")

        // Check if widget needs configuration
        if (appWidgetInfo.configure != null) {
            Log.d(TAG, "Widget requires configuration")
            val configIntent = Intent().apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }

            try {
                startActivityForResult(configIntent, REQUEST_CREATE_APPWIDGET)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch widget configuration", e)
                // If configuration fails, try to add widget anyway
                onWidgetConfigured(widgetId)
            }
        } else {
            // Widget doesn't need configuration
            Log.d(TAG, "Widget does not require configuration, completing setup")
            onWidgetConfigured(widgetId)
        }
    }

    private fun onWidgetConfigured(widgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)

        if (appWidgetInfo == null) {
            Log.e(TAG, "Failed to get widget info after configuration")
            cleanup()
            finish()
            return
        }

        // Send broadcast with the widget information
        sendWidgetResult(widgetId, appWidgetInfo)

        // Also return result for any parent activity
        val resultIntent = Intent().apply {
            putExtra(EXTRA_WIDGET_ID, widgetId)
            putExtra(EXTRA_WIDGET_INFO, appWidgetInfo.provider.flattenToString())
        }

        setResult(Activity.RESULT_OK, resultIntent)
        Log.d(TAG, "Returning widget ID $widgetId to caller")

        finish()
    }

    private fun sendWidgetResult(widgetId: Int, widgetInfo: AppWidgetProviderInfo) {
        // Send broadcast with widget selection result
        val intent = Intent(WidgetResultReceiver.ACTION_WIDGET_SELECTED).apply {
            putExtra(WidgetResultReceiver.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetResultReceiver.EXTRA_WIDGET_PROVIDER, widgetInfo.provider.flattenToString())
            putExtra(WidgetResultReceiver.EXTRA_WIDGET_LABEL, widgetInfo.loadLabel(packageManager))
            setPackage(packageName) // Ensure it's only received by our app
        }

        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent for widget: $widgetId - ${widgetInfo.label}")
    }

    private fun sendCancellationBroadcast() {
        val intent = Intent(WidgetResultReceiver.ACTION_WIDGET_CANCELLED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent for cancellation")
    }

    private fun tryDirectWidgetAdd(widgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        Log.d(TAG, "Trying direct widget add without full binding for: ${appWidgetInfo.label}")

        // Check one more time if binding is possible (in case permission was granted elsewhere)
        val lastTryBind = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, appWidgetInfo.provider)
        } catch (e: Exception) {
            Log.e(TAG, "Final bind attempt failed", e)
            false
        }

        if (lastTryBind) {
            Log.d(TAG, "Surprise! Widget binding succeeded on final attempt")
            handleSuccessfulBinding(widgetId, appWidgetInfo)
            return
        }

        // Send the widget info back even without binding
        // The overlay can try to display it as best as possible
        Log.w(TAG, "Adding widget without proper binding - functionality may be limited")
        sendWidgetResult(widgetId, appWidgetInfo)

        Toast.makeText(
            this,
            "Widget added (may have limited functionality)",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }

    private fun cleanup() {
        if (pendingWidgetId != -1) {
            try {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                Log.d(TAG, "Cleaned up widget ID: $pendingWidgetId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cleanup widget ID", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appWidgetHost.stopListening()
    }
}
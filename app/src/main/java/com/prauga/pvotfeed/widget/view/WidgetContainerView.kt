package com.prauga.pvotfeed.widget.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children

/**
 * Container view for hosting multiple widgets
 * Manages widget layout and interactions
 */
class WidgetContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WidgetContainerView"
        private const val DEFAULT_WIDGET_SPACING = 16 // dp
    }

    init {
        orientation = VERTICAL
        val spacing = (DEFAULT_WIDGET_SPACING * context.resources.displayMetrics.density).toInt()
        dividerDrawable = context.getDrawable(android.R.color.transparent)
        showDividers = SHOW_DIVIDER_MIDDLE
        dividerPadding = spacing
    }

    /**
     * Add a widget view to the container
     */
    fun addWidgetView(widgetView: View, position: Int = -1) {
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            val margin = (8 * context.resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }
        widgetView.layoutParams = params

        if (position >= 0 && position < childCount) {
            addView(widgetView, position)
        } else {
            addView(widgetView)
        }

        Log.d(TAG, "Widget view added at position: ${if (position >= 0) position else childCount - 1}")
    }

    /**
     * Remove a widget view
     */
    fun removeWidgetView(widgetView: View) {
        removeView(widgetView)
        Log.d(TAG, "Widget view removed")
    }

    /**
     * Clear all widgets
     */
    fun clearAllWidgets() {
        removeAllViews()
        Log.d(TAG, "All widgets cleared")
    }

    /**
     * Get widget count
     */
    fun getWidgetCount(): Int = childCount

    /**
     * Get widget at position
     */
    fun getWidgetAt(position: Int): View? {
        return if (position >= 0 && position < childCount) {
            getChildAt(position)
        } else null
    }

    /**
     * Find widget view by tag (widget ID)
     */
    fun findWidgetById(widgetId: Int): View? {
        return children.find { it.tag == widgetId }
    }

    /**
     * Reorder widgets
     */
    fun reorderWidget(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= childCount ||
            toPosition < 0 || toPosition >= childCount) {
            return
        }

        val view = getChildAt(fromPosition)
        removeViewAt(fromPosition)
        addView(view, toPosition)

        Log.d(TAG, "Widget reordered from $fromPosition to $toPosition")
    }

    /**
     * Set edit mode for widgets
     */
    fun setEditMode(enabled: Boolean) {
        children.forEach { widgetView ->
            widgetView.isClickable = !enabled
            widgetView.alpha = if (enabled) 0.8f else 1.0f
        }
        Log.d(TAG, "Edit mode: $enabled")
    }
}
package com.prauga.pvotfeed.widget.view

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prauga.pvotfeed.R

/**
 * Custom view for selecting widgets
 * Embeds directly in the overlay instead of showing as a dialog
 */
class WidgetPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WidgetPickerView"
        private const val GRID_COLUMNS = 3
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var titleView: TextView
    private lateinit var closeButton: Button
    private val widgetList = mutableListOf<WidgetItem>()

    private var onWidgetSelectedListener: ((AppWidgetProviderInfo) -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null

    init {
        setupView()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.view_widget_picker, this, true)

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        titleView = findViewById(R.id.titleView)
        closeButton = findViewById(R.id.btnClose)

        recyclerView.layoutManager = GridLayoutManager(context, GRID_COLUMNS)
        recyclerView.adapter = WidgetAdapter()

        closeButton.setOnClickListener {
            onCloseListener?.invoke()
        }

        // Set background to block touches to underlying views
        setBackgroundColor(context.getColor(android.R.color.background_light))
        isClickable = true
        isFocusable = true
    }

    fun setOnWidgetSelectedListener(listener: (AppWidgetProviderInfo) -> Unit) {
        onWidgetSelectedListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun show() {
        visibility = View.VISIBLE
        loadWidgets()
    }

    fun hide() {
        visibility = View.GONE
    }

    private fun loadWidgets() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val providers = appWidgetManager.installedProviders

        widgetList.clear()

        for (info in providers) {
            try {
                val item = WidgetItem(
                    info = info,
                    label = info.loadLabel(context.packageManager),
                    icon = info.loadIcon(context, android.util.DisplayMetrics.DENSITY_DEFAULT)
                        ?: info.loadPreviewImage(context, android.util.DisplayMetrics.DENSITY_DEFAULT)
                )
                widgetList.add(item)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load widget info: ${info.provider}", e)
            }
        }

        // Sort by label
        widgetList.sortBy { it.label }

        progressBar.visibility = View.GONE

        if (widgetList.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter?.notifyDataSetChanged()
        }

        Log.d(TAG, "Loaded ${widgetList.size} widgets")
    }

    private data class WidgetItem(
        val info: AppWidgetProviderInfo,
        val label: String,
        val icon: Drawable?
    )

    private inner class WidgetAdapter : RecyclerView.Adapter<WidgetViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_picker, parent, false)
            return WidgetViewHolder(view)
        }

        override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
            holder.bind(widgetList[position])
        }

        override fun getItemCount(): Int = widgetList.size
    }

    private inner class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.widgetIcon)
        private val labelView: TextView = itemView.findViewById(R.id.widgetLabel)
        private val packageView: TextView = itemView.findViewById(R.id.widgetPackage)

        init {
            Log.d(TAG, "WidgetViewHolder created")
        }

        fun bind(item: WidgetItem) {
            Log.d(TAG, "Binding widget: ${item.label} from ${item.info.provider.packageName}")

            labelView.text = item.label
            packageView.text = item.info.provider.packageName
            packageView.visibility = View.GONE // Hide package name for cleaner look

            if (item.icon != null) {
                iconView.setImageDrawable(item.icon)
            } else {
                // Set a default icon if none available
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener {
                Log.d(TAG, "Widget item clicked: ${item.label}")
                Log.d(TAG, "Listener is null? ${onWidgetSelectedListener == null}")

                if (onWidgetSelectedListener != null) {
                    Log.d(TAG, "Invoking selection listener for: ${item.label}")
                    onWidgetSelectedListener?.invoke(item.info)
                    Log.d(TAG, "Selection listener invoked, hiding picker")
                    hide()
                } else {
                    Log.e(TAG, "onWidgetSelectedListener is null!")
                }
            }

            // Also log that click listener was set
            Log.d(TAG, "Click listener set for: ${item.label}")
        }
    }
}
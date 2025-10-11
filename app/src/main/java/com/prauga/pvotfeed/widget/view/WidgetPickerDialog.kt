package com.prauga.pvotfeed.widget.view

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prauga.pvotfeed.R

/**
 * Custom dialog for selecting widgets
 * Shows all available widgets in a grid layout
 */
class WidgetPickerDialog(
    context: Context,
    private val onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) : Dialog(context) {

    companion object {
        private const val TAG = "WidgetPickerDialog"
        private const val GRID_COLUMNS = 3
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private val widgetList = mutableListOf<WidgetItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_widget_picker)

        // Set dialog size
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupViews()
        loadWidgets()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        recyclerView.layoutManager = GridLayoutManager(context, GRID_COLUMNS)
        recyclerView.adapter = WidgetAdapter()

        findViewById<View>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
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

        fun bind(item: WidgetItem) {
            labelView.text = item.label
            packageView.text = item.info.provider.packageName

            if (item.icon != null) {
                iconView.setImageDrawable(item.icon)
            } else {
                // Set a default icon if none available
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener {
                Log.d(TAG, "Widget selected: ${item.label}")
                onWidgetSelected(item.info)
                dismiss()
            }
        }
    }
}
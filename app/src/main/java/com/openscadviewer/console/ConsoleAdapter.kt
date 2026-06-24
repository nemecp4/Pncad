package com.openscadviewer.console

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.openscadviewer.R

class ConsoleAdapter : ListAdapter<LogEntry, ConsoleAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timestamp: TextView = view.findViewById(R.id.logTimestamp)
        val severity: TextView = view.findViewById(R.id.logSeverity)
        val message: TextView = view.findViewById(R.id.logMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.timestamp.text = entry.formattedTimestamp()
        holder.severity.text = entry.severity.name
        holder.message.text = entry.message

        val color = when (entry.severity) {
            LogSeverity.INFO -> Color.parseColor("#B0BEC5")
            LogSeverity.WARN -> Color.parseColor("#FFB74D")
            LogSeverity.ERROR -> Color.parseColor("#EF5350")
        }
        holder.severity.setTextColor(color)
        holder.message.setTextColor(color)
    }

    /**
     * Returns all log entries as a single string for clipboard copying.
     */
    fun getAllLogText(): String {
        val sb = StringBuilder()
        for (i in 0 until itemCount) {
            val entry = getItem(i)
            sb.append("${entry.formattedTimestamp()} ${entry.severity.name} ${entry.message}\n")
        }
        return sb.toString().trimEnd()
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(old: LogEntry, new: LogEntry) =
            old.timestamp == new.timestamp && old.message == new.message

        override fun areContentsTheSame(old: LogEntry, new: LogEntry) =
            old == new
    }
}

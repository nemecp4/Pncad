package com.openscadviewer.file

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.openscadviewer.R

/**
 * RecyclerView adapter for the Open Files menu popup.
 * Displays file sessions with a dirty asterisk prefix and
 * highlighted background for the active file.
 */
class OpenFilesAdapter(
    private val onFileClicked: (sessionId: String) -> Unit
) : RecyclerView.Adapter<OpenFilesAdapter.ViewHolder>() {

    private var sessions: List<FileSession> = emptyList()
    private var activeSessionId: String? = null

    /**
     * Update the adapter data and refresh the list.
     */
    fun updateData(sessions: List<FileSession>, activeSessionId: String?) {
        this.sessions = sessions
        this.activeSessionId = activeSessionId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_open_file, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val isActive = session.id == activeSessionId

        // Display name with dirty asterisk prefix (requirement 6.6)
        val displayText = if (session.isDirty) {
            "*${session.displayName}"
        } else {
            session.displayName
        }
        holder.textView.text = displayText

        // Active file highlighted background (requirement 6.4)
        if (isActive) {
            // Semi-transparent accent color for active item
            holder.textView.setBackgroundColor(Color.parseColor("#40FF6F00"))
        } else {
            holder.textView.setBackgroundResource(R.color.code_background)
        }

        holder.textView.setOnClickListener {
            onFileClicked(session.id)
        }
    }

    override fun getItemCount(): Int = sessions.size

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}

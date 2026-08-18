package com.openscadviewer.file

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.openscadviewer.R

/**
 * PopupWindow displaying the list of currently open file sessions.
 * Positions below the trigger icon and delegates file selection
 * to the provided callback (typically FileViewModel.switchToFile).
 */
class OpenFilesMenuPopup(
    private val context: Context,
    private val onFileSelected: (sessionId: String) -> Unit
) {
    private var popupWindow: PopupWindow? = null
    private val adapter = OpenFilesAdapter { sessionId ->
        onFileSelected(sessionId)
        dismiss()
    }

    /**
     * Show the open files popup below the given anchor view.
     *
     * @param anchor The view to position the popup below
     * @param sessions The ordered list of open file sessions (MRU first)
     * @param activeSessionId The ID of the currently active session
     */
    fun show(anchor: View, sessions: List<FileSession>, activeSessionId: String?) {
        dismiss() // Close any existing popup

        val inflater = LayoutInflater.from(context)
        val contentView = inflater.inflate(R.layout.popup_open_files_menu, null)

        val recyclerView = contentView.findViewById<RecyclerView>(R.id.recyclerOpenFiles)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        adapter.updateData(sessions, activeSessionId)

        // Constrain popup max height to 300dp for scrollability
        val maxHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 300f, context.resources.displayMetrics
        ).toInt()

        popupWindow = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable — allows dismiss on outside touch
        ).apply {
            elevation = 8f
            isOutsideTouchable = true
            // Measure content to determine if we need to cap height
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (contentView.measuredHeight > maxHeightPx) {
                height = maxHeightPx
            }
            showAsDropDown(anchor)
        }
    }

    /**
     * Dismiss the popup if it is currently showing.
     */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    /**
     * Check if the popup is currently visible.
     */
    fun isShowing(): Boolean = popupWindow?.isShowing == true
}

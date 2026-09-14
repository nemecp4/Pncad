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
 * A single popup that merges the former File menu and Open Files menu.
 *
 * The top section holds the file operations (New, Open, Save, Save As, Close);
 * below a divider it lists the currently open file sessions. This replaces the
 * two separate icon buttons with one entry point.
 */
class CombinedFileMenuPopup(
    private val context: Context,
    private val onNew: () -> Unit,
    private val onOpen: () -> Unit,
    private val onSave: () -> Unit,
    private val onSaveAs: () -> Unit,
    private val onClose: () -> Unit,
    private val onFileSelected: (sessionId: String) -> Unit
) {
    private var popupWindow: PopupWindow? = null
    private val adapter = OpenFilesAdapter { sessionId ->
        onFileSelected(sessionId)
        dismiss()
    }

    /**
     * Shows the combined menu below [anchor].
     *
     * @param hasActiveSession When false, Save/Save As/Close are disabled (only New/Open active).
     * @param sessions Ordered list of open file sessions (MRU first).
     * @param activeSessionId ID of the currently active session, if any.
     */
    fun show(
        anchor: View,
        hasActiveSession: Boolean,
        sessions: List<FileSession>,
        activeSessionId: String?
    ) {
        if (isShowing()) {
            dismiss()
            return
        }

        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.popup_file_menu_combined, null)

        wireFileOperations(popupView, hasActiveSession)
        bindOpenFiles(popupView, sessions, activeSessionId)

        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable — auto-dismisses on outside touch
        ).apply {
            elevation = 8f
            isOutsideTouchable = true

            // Cap overall height so a long open-files list stays scrollable.
            val maxHeightPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 420f, context.resources.displayMetrics
            ).toInt()
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (popupView.measuredHeight > maxHeightPx) {
                height = maxHeightPx
            }
            showAsDropDown(anchor)
        }
    }

    private fun wireFileOperations(popupView: View, hasActiveSession: Boolean) {
        popupView.findViewById<View>(R.id.menuItemNew).setOnClickListener {
            dismiss()
            onNew()
        }
        popupView.findViewById<View>(R.id.menuItemOpen).setOnClickListener {
            dismiss()
            onOpen()
        }

        val menuItemSave = popupView.findViewById<View>(R.id.menuItemSave)
        val menuItemSaveAs = popupView.findViewById<View>(R.id.menuItemSaveAs)
        val menuItemClose = popupView.findViewById<View>(R.id.menuItemClose)

        if (hasActiveSession) {
            menuItemSave.setOnClickListener {
                dismiss()
                onSave()
            }
            menuItemSaveAs.setOnClickListener {
                dismiss()
                onSaveAs()
            }
            menuItemClose.setOnClickListener {
                dismiss()
                onClose()
            }
        } else {
            val disabledAlpha = 0.4f
            for (item in listOf(menuItemSave, menuItemSaveAs, menuItemClose)) {
                item.alpha = disabledAlpha
                item.isClickable = false
                item.isFocusable = false
            }
        }
    }

    private fun bindOpenFiles(
        popupView: View,
        sessions: List<FileSession>,
        activeSessionId: String?
    ) {
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recyclerOpenFiles)
        val emptyText = popupView.findViewById<View>(R.id.noOpenFilesText)

        if (sessions.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
            adapter.updateData(sessions, activeSessionId)
        }
    }

    /**
     * Dismisses the popup if it is currently showing.
     */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    /**
     * Returns true if the popup is currently visible.
     */
    fun isShowing(): Boolean = popupWindow?.isShowing == true
}

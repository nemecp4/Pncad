package com.openscadviewer.file

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.openscadviewer.R

/**
 * A popup menu for file operations (Open, Save, Save As, Close).
 * Displayed as a PopupWindow anchored below the file menu icon button.
 */
class FileMenuPopup(
    private val context: Context,
    private val onNew: () -> Unit,
    private val onOpen: () -> Unit,
    private val onSave: () -> Unit,
    private val onSaveAs: () -> Unit,
    private val onClose: () -> Unit
) {
    private var popupWindow: PopupWindow? = null

    /**
     * Shows the file menu popup below the given anchor view.
     *
     * @param anchor The view to anchor the popup below
     * @param hasActiveSession When false, Save/Save As/Close items are visually
     *        disabled (grayed out and non-clickable). Only Open remains active.
     *        Requirements: 5.3, 7.5
     */
    fun show(anchor: View, hasActiveSession: Boolean = true) {
        if (isShowing()) {
            dismiss()
            return
        }

        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.popup_file_menu, null)

        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable — auto-dismisses on outside touch
        ).apply {
            elevation = 8f
            setBackgroundDrawable(null)
        }

        // Set up click listeners
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
            // Disable Save, Save As, Close when no active session
            val disabledAlpha = 0.4f
            menuItemSave.alpha = disabledAlpha
            menuItemSave.isClickable = false
            menuItemSave.isFocusable = false

            menuItemSaveAs.alpha = disabledAlpha
            menuItemSaveAs.isClickable = false
            menuItemSaveAs.isFocusable = false

            menuItemClose.alpha = disabledAlpha
            menuItemClose.isClickable = false
            menuItemClose.isFocusable = false
        }

        // Position below the anchor
        popupWindow?.showAsDropDown(anchor)
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

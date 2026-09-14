package com.openscadviewer.file

import android.view.View

/**
 * Controller for the combined file menu.
 *
 * The menu is triggered from the toolbar's navigation (top-left) icon and shown
 * as a popup anchored to that toolbar. The popup holds both the file operations
 * (New/Open/Save/Save As/Close) and the list of open files. Outside-tap dismissal
 * is handled by PopupWindow's focusable=true setting; the menu starts collapsed.
 */
class FileBarController(
    private val anchor: View,
    private val combinedMenuPopup: CombinedFileMenuPopup,
    private val getSessionsData: () -> Pair<List<FileSession>, String?>
) {

    /**
     * Toggle the combined file menu popup. Wire this to the trigger's click.
     */
    fun toggle() {
        if (combinedMenuPopup.isShowing()) {
            combinedMenuPopup.dismiss()
        } else {
            val (sessions, activeId) = getSessionsData()
            combinedMenuPopup.show(
                anchor = anchor,
                hasActiveSession = activeId != null,
                sessions = sessions,
                activeSessionId = activeId
            )
        }
    }

    /**
     * Dismiss the open menu. Useful when the activity needs to
     * programmatically close it (e.g., on configuration change or navigation).
     */
    fun dismissAll() {
        combinedMenuPopup.dismiss()
    }
}

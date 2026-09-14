package com.openscadviewer.file

import android.view.View

/**
 * Controller managing the File Bar button and its combined popup.
 *
 * Tapping the File icon toggles a single popup that holds both the file
 * operations (New/Open/Save/Save As/Close) and the list of open files.
 * Outside-tap dismissal is handled by PopupWindow's focusable=true setting.
 * On app launch, the menu starts collapsed.
 */
class FileBarController(
    private val btnFileMenu: View,
    private val combinedMenuPopup: CombinedFileMenuPopup,
    private val getSessionsData: () -> Pair<List<FileSession>, String?>
) {

    /**
     * Wire the click listener on the file menu button.
     * Must be called after the view and popup are initialized.
     */
    fun setup() {
        btnFileMenu.setOnClickListener { toggleMenu() }
    }

    /**
     * Toggle the combined file menu popup.
     */
    private fun toggleMenu() {
        if (combinedMenuPopup.isShowing()) {
            combinedMenuPopup.dismiss()
        } else {
            val (sessions, activeId) = getSessionsData()
            combinedMenuPopup.show(
                anchor = btnFileMenu,
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

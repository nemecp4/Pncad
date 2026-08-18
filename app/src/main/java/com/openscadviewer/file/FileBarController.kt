package com.openscadviewer.file

import android.view.View

/**
 * Controller managing the File Bar toggle and mutual-exclusion behavior.
 *
 * Handles:
 * - Tapping the File icon toggles the File Menu (closes Open Files Menu if open)
 * - Tapping the Open Files icon toggles the Open Files Menu (closes File Menu if open)
 * - Outside-tap dismissal is handled by PopupWindow's focusable=true setting
 * - On app launch, both menus start collapsed (natural initial state)
 *
 * Requirements: 1.6, 1.7, 1.8, 1.9
 */
class FileBarController(
    private val btnFileMenu: View,
    private val btnOpenFilesMenu: View,
    private val fileMenuPopup: FileMenuPopup,
    private val openFilesMenuPopup: OpenFilesMenuPopup,
    private val getSessionsData: () -> Pair<List<FileSession>, String?>
) {

    /**
     * Wire click listeners on the two icon buttons.
     * Must be called after the views and popups are initialized.
     */
    fun setup() {
        btnFileMenu.setOnClickListener { toggleFileMenu() }
        btnOpenFilesMenu.setOnClickListener { toggleOpenFilesMenu() }
    }

    /**
     * Toggle the File Menu popup.
     * - If File Menu is showing, dismiss it (toggle off).
     * - If Open Files Menu is showing, dismiss it and show File Menu.
     * - If nothing is showing, show File Menu.
     */
    private fun toggleFileMenu() {
        if (fileMenuPopup.isShowing()) {
            fileMenuPopup.dismiss()
        } else {
            openFilesMenuPopup.dismiss()
            val (_, activeId) = getSessionsData()
            fileMenuPopup.show(btnFileMenu, hasActiveSession = activeId != null)
        }
    }

    /**
     * Toggle the Open Files Menu popup.
     * - If Open Files Menu is showing, dismiss it (toggle off).
     * - If File Menu is showing, dismiss it and show Open Files Menu.
     * - If nothing is showing, show Open Files Menu.
     */
    private fun toggleOpenFilesMenu() {
        if (openFilesMenuPopup.isShowing()) {
            openFilesMenuPopup.dismiss()
        } else {
            fileMenuPopup.dismiss()
            val (sessions, activeId) = getSessionsData()
            openFilesMenuPopup.show(btnOpenFilesMenu, sessions, activeId)
        }
    }

    /**
     * Dismiss all open menus. Useful when the activity needs to
     * programmatically close menus (e.g., on configuration change or navigation).
     */
    fun dismissAll() {
        fileMenuPopup.dismiss()
        openFilesMenuPopup.dismiss()
    }
}

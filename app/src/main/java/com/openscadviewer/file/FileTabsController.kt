package com.openscadviewer.file

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.openscadviewer.R

/**
 * Renders the open file sessions as a horizontal strip of tabs.
 *
 * Each tab shows the file's display name (prefixed with "*" when it has
 * unsaved changes) and a close affordance. The active session's tab is
 * highlighted. Tapping a tab switches to that file; tapping its close icon
 * closes it.
 *
 * The strip is hidden entirely when there are no open sessions.
 */
class FileTabsController(
    private val scrollView: HorizontalScrollView,
    private val container: LinearLayout,
    private val onTabSelected: (sessionId: String) -> Unit,
    private val onTabClosed: (sessionId: String) -> Unit
) {
    private companion object {
        val ACTIVE_BG: Int = Color.parseColor("#40FF6F00")
        const val ACTIVE_TEXT: Int = Color.WHITE
        val INACTIVE_TEXT: Int = Color.parseColor("#B0FFFFFF")
    }

    /**
     * Rebuild the tab strip from the given sessions.
     *
     * @param sessions Ordered list of open sessions.
     * @param activeSessionId The currently active session id, or null.
     */
    fun render(sessions: List<FileSession>, activeSessionId: String?) {
        container.removeAllViews()

        if (sessions.isEmpty()) {
            scrollView.visibility = View.GONE
            return
        }
        scrollView.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(container.context)
        var activeTab: View? = null

        for (session in sessions) {
            val tab = inflater.inflate(R.layout.item_file_tab, container, false)
            val name = tab.findViewById<TextView>(R.id.txtTabName)
            val close = tab.findViewById<ImageView>(R.id.btnTabClose)

            name.text = if (session.isDirty) "*${session.displayName}" else session.displayName

            val isActive = session.id == activeSessionId
            if (isActive) {
                tab.setBackgroundColor(ACTIVE_BG)
                name.setTextColor(ACTIVE_TEXT)
                close.setColorFilter(ACTIVE_TEXT)
                activeTab = tab
            } else {
                tab.setBackgroundResource(R.color.code_background)
                name.setTextColor(INACTIVE_TEXT)
                close.setColorFilter(INACTIVE_TEXT)
            }

            tab.setOnClickListener { onTabSelected(session.id) }
            close.setOnClickListener { onTabClosed(session.id) }

            container.addView(tab)
        }

        // Keep the active tab visible.
        activeTab?.let { view ->
            scrollView.post {
                val target = view.left - (scrollView.width - view.width) / 2
                scrollView.smoothScrollTo(target.coerceAtLeast(0), 0)
            }
        }
    }
}

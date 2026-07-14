package com.openscadviewer.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText

class CompletionPopup(
    private val context: Context,
    private val anchorView: EditText,
    private val onItemSelected: (CompletionItem) -> Unit
) {
    companion object {
        const val MAX_VISIBLE_ITEMS = 5
        private const val BACKGROUND_COLOR = 0xFF1E1E1E.toInt()
        private const val TEXT_COLOR = 0xFFD4D4D4.toInt()
        private const val HIGHLIGHT_COLOR = 0xFF264F78.toInt()
        private const val TEXT_SIZE_SP = 13f
        private const val ITEM_PADDING_DP = 8
    }

    private var popupWindow: PopupWindow? = null
    private var isShowing = false

    fun show(items: List<CompletionItem>, cursorLine: Int, cursorCol: Int) {
        dismiss()
        if (items.isEmpty()) return

        val contentView = buildContentView(items)
        val popup = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false  // not focusable - let EditText keep focus
        )
        popup.isOutsideTouchable = true
        popup.setOnDismissListener { isShowing = false }

        // Calculate anchor position based on cursor coordinates
        val (x, y) = calculatePosition(cursorLine, cursorCol)
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        popupWindow = popup
        isShowing = true
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
        isShowing = false
    }

    fun isShowing(): Boolean = isShowing

    fun update(items: List<CompletionItem>, cursorLine: Int, cursorCol: Int) {
        if (items.isEmpty()) {
            dismiss()
        } else {
            show(items, cursorLine, cursorCol)
        }
    }

    private fun calculatePosition(cursorLine: Int, cursorCol: Int): Pair<Int, Int> {
        val layout = anchorView.layout ?: return Pair(0, 0)
        val lineTop = layout.getLineTop(cursorLine)
        val lineBottom = layout.getLineBottom(cursorLine)
        val x = layout.getPrimaryHorizontal(
            anchorView.text.let {
                var pos = 0
                for (i in 0 until cursorLine) pos = layout.getLineEnd(i)
                pos + cursorCol
            }
        ).toInt()

        // Get EditText location on screen
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        val screenY = location[1] + lineBottom - anchorView.scrollY
        val screenX = location[0] + x - anchorView.scrollX

        return Pair(screenX, screenY)
    }

    private fun buildContentView(items: List<CompletionItem>): ScrollView {
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = items.size > MAX_VISIBLE_ITEMS
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND_COLOR)
        }

        for (item in items) {
            val textView = TextView(context).apply {
                text = item.text
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
                setTextColor(TEXT_COLOR)
                val pad = dpToPx(ITEM_PADDING_DP)
                setPadding(pad * 2, pad, pad * 2, pad)
                setOnClickListener { onItemSelected(item) }
            }
            container.addView(textView)
        }

        scrollView.addView(container)
        return scrollView
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}

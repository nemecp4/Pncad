package com.openscadviewer

/**
 * Determines whether the split pane layout should fall back to single-pane mode.
 *
 * Returns true if each pane would be narrower than 200dp after subtracting the divider,
 * indicating that the split layout would be too cramped to be usable.
 */
fun shouldFallbackToSinglePane(availableWidthDp: Float, dividerWidthDp: Float): Boolean {
    val paneWidth = (availableWidthDp - dividerWidthDp) / 2f
    return paneWidth < 200f
}

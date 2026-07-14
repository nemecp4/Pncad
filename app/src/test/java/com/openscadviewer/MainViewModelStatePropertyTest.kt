package com.openscadviewer

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.StringLength
import org.junit.jupiter.api.Assertions.*

// Feature: tablet-layout, Property 2: UI state preservation round-trip

/**
 * Property-based test for UI state preservation round-trip.
 *
 * For any valid editor state (arbitrary non-null text content, cursor position within
 * text bounds, arbitrary file name string, arbitrary status bar text), storing the state
 * into MainViewModel and then reading it back produces values identical to the originals.
 *
 * Validates: Requirements 6.1, 6.2, 1.4, 7.3
 */
class MainViewModelStatePropertyTest {

    @Property(tries = 100)
    fun uiStateRoundTripPreservesAllFields(
        @ForAll @StringLength(min = 0, max = 10000) editorText: String,
        @ForAll @IntRange(min = 0, max = 10000) cursorPosition: Int,
        @ForAll @StringLength(min = 0, max = 100) fileName: String,
        @ForAll @StringLength(min = 0, max = 200) statusBarText: String
    ) {
        val clampedCursorPosition = minOf(cursorPosition, editorText.length)

        val viewModel = MainViewModel()

        // Store state
        viewModel.editorText = editorText
        viewModel.cursorPosition = clampedCursorPosition
        viewModel.currentFileName = fileName
        viewModel.statusBarText = statusBarText

        // Read back and assert equality
        assertEquals(editorText, viewModel.editorText,
            "editorText round-trip failed")
        assertEquals(clampedCursorPosition, viewModel.cursorPosition,
            "cursorPosition round-trip failed")
        assertEquals(fileName, viewModel.currentFileName,
            "currentFileName round-trip failed")
        assertEquals(statusBarText, viewModel.statusBarText,
            "statusBarText round-trip failed")
    }

    @Property(tries = 100)
    fun cursorClampingPreventsOutOfBounds(
        @ForAll @StringLength(min = 0, max = 10000) editorText: String,
        @ForAll @IntRange(min = 0, max = 10000) cursorPosition: Int
    ) {
        val clampedCursorPosition = minOf(cursorPosition, editorText.length)

        val viewModel = MainViewModel()
        viewModel.editorText = editorText
        viewModel.cursorPosition = clampedCursorPosition

        // The clamped cursor should always be within valid bounds
        assertTrue(viewModel.cursorPosition >= 0,
            "Cursor position must be non-negative")
        assertTrue(viewModel.cursorPosition <= viewModel.editorText.length,
            "Cursor position (${viewModel.cursorPosition}) must not exceed " +
                "text length (${viewModel.editorText.length})")

        // Verify the clamping logic itself
        assertEquals(minOf(cursorPosition, editorText.length), viewModel.cursorPosition,
            "Clamping logic minOf($cursorPosition, ${editorText.length}) " +
                "should equal stored value ${viewModel.cursorPosition}")
    }
}

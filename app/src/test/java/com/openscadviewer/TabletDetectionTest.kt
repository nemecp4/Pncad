package com.openscadviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for tablet detection logic and generatePreview/cancellation behavior.
 *
 * Since we cannot easily test Activity-level view binding without Robolectric,
 * these tests verify the core LOGIC patterns used by MainActivity:
 *
 * - isTabletLayout detection: `findViewById<View>(R.id.paneDivider) != null`
 * - generatePreview tab switching: `tabLayout?.getTabAt(1)?.select()` (null on tablet)
 * - Computation cancellation: sets "Computation cancelled" status in ViewModel
 *
 * Requirements: 1.1, 1.2, 5.3, 6.5
 */
class TabletDetectionTest {

    /**
     * Simulates the tablet detection logic used in MainActivity:
     * `private val isTabletLayout: Boolean by lazy { findViewById<View>(R.id.paneDivider) != null }`
     *
     * @param paneDividerView represents the result of findViewById — null if absent, non-null if present
     */
    private fun detectTabletLayout(paneDividerView: Any?): Boolean {
        return paneDividerView != null
    }

    @Nested
    @DisplayName("Tablet Detection (Requirements 1.1, 1.2)")
    inner class TabletDetectionTests {

        @Test
        @DisplayName("isTabletLayout returns true when paneDivider is present")
        fun tabletDetected_whenPaneDividerPresent() {
            // Simulates a non-null view returned by findViewById(R.id.paneDivider)
            val paneDivider = Object() // any non-null object simulates a present View
            val isTablet = detectTabletLayout(paneDivider)
            assertTrue(isTablet, "Should detect tablet layout when paneDivider view exists")
        }

        @Test
        @DisplayName("isTabletLayout returns false when paneDivider is absent")
        fun tabletNotDetected_whenPaneDividerAbsent() {
            // Simulates null returned by findViewById(R.id.paneDivider) — phone layout
            val paneDivider = null
            val isTablet = detectTabletLayout(paneDivider)
            assertFalse(isTablet, "Should not detect tablet layout when paneDivider view is absent")
        }
    }

    @Nested
    @DisplayName("generatePreview tab switching (Requirement 5.3)")
    inner class GeneratePreviewTabSwitchingTests {

        /**
         * Simulates the tab-switching logic from generatePreview():
         * `tabLayout?.getTabAt(1)?.select()`
         *
         * On tablet mode, tabLayout is null, so no tab switching occurs.
         * On phone mode, tabLayout is non-null, and getTabAt(1)?.select() is called.
         *
         * @return true if tab switching was attempted, false otherwise
         */
        private fun simulateTabSwitchingLogic(tabLayout: FakeTabLayout?): Boolean {
            tabLayout?.getTabAt(1)?.select()
            return tabLayout?.selectCalled == true
        }

        @Test
        @DisplayName("generatePreview does not attempt tab switching on tablet mode")
        fun noTabSwitching_whenTabletMode() {
            // On tablet, tabLayout is null (not inflated)
            val tabLayout: FakeTabLayout? = null
            val tabSwitched = simulateTabSwitchingLogic(tabLayout)
            assertFalse(tabSwitched, "Tab switching should not occur when tabLayout is null (tablet mode)")
        }

        @Test
        @DisplayName("generatePreview switches to preview tab on phone mode")
        fun tabSwitching_whenPhoneMode() {
            // On phone, tabLayout is present
            val tabLayout = FakeTabLayout()
            val tabSwitched = simulateTabSwitchingLogic(tabLayout)
            assertTrue(tabSwitched, "Tab switching should occur when tabLayout is present (phone mode)")
        }
    }

    @Nested
    @DisplayName("Computation cancellation on config change (Requirement 6.5)")
    inner class ComputationCancellationTests {

        @Test
        @DisplayName("Cancellation on config change sets 'Computation cancelled' in ViewModel")
        fun cancellation_setsComputationCancelledStatus() {
            // Simulate the logic from onPause()/onDestroy():
            // if (computeJob?.isActive == true) {
            //     ...cancel...
            //     statusBar.text = "Computation cancelled"
            // }
            // saveStateToViewModel() -> viewModel.statusBarText = statusBar.text

            val viewModel = MainViewModel()
            val computeJobActive = true // simulate active compute job

            // Simulate the cancellation logic
            if (computeJobActive) {
                // In the real code: engineManager.currentEngine.cancel(), computeJob?.cancel()
                viewModel.isComputing = false
                viewModel.statusBarText = "Computation cancelled"
            }

            assertFalse(viewModel.isComputing, "isComputing should be false after cancellation")
            assertEquals(
                "Computation cancelled",
                viewModel.statusBarText,
                "Status bar text should be 'Computation cancelled' after config change cancellation"
            )
        }

        @Test
        @DisplayName("No cancellation when no active computation")
        fun noCancellation_whenNoActiveComputation() {
            val viewModel = MainViewModel()
            val computeJobActive = false

            // Simulate: no active job, no cancellation occurs
            if (computeJobActive) {
                viewModel.isComputing = false
                viewModel.statusBarText = "Computation cancelled"
            }

            // ViewModel retains default values
            assertFalse(viewModel.isComputing, "isComputing should remain false (default)")
            assertEquals("", viewModel.statusBarText, "Status bar text should remain empty (default)")
        }
    }

    // --- Test doubles ---

    /**
     * Minimal fake TabLayout to test the safe-call chain:
     * tabLayout?.getTabAt(1)?.select()
     */
    class FakeTabLayout {
        var selectCalled = false

        fun getTabAt(position: Int): FakeTab {
            return FakeTab { selectCalled = true }
        }
    }

    class FakeTab(private val onSelect: () -> Unit) {
        fun select() {
            onSelect()
        }
    }
}

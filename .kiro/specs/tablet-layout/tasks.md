# Implementation Plan: Tablet Layout

## Overview

This plan implements a tablet-optimized split pane layout for the Pncad app. On tablets (sw ≥ 600dp), the app shows Code and Preview panes simultaneously instead of switching between tabs. The implementation creates resource-qualified layout XMLs, a MainViewModel for state preservation across configuration changes, and modifies MainActivity to conditionally set up phone vs. tablet UI paths. Detection uses a sentinel `paneDivider` view with fallback logic for narrow panes.

## Tasks

- [x] 1. Create MainViewModel for state preservation
  - [x] 1.1 Create MainViewModel class
    - Create `app/src/main/java/com/openscadviewer/MainViewModel.kt`
    - Extend `androidx.lifecycle.ViewModel`
    - Add editor state fields: `editorText: String`, `cursorPosition: Int`, `currentFileName: String`, `statusBarText: String`
    - Add renderer state fields: `meshVertices: FloatArray?`, `meshNormals: FloatArray?`, `meshColors: FloatArray?`, `triangleCount: Int`
    - Add camera state fields: `cameraRotX: Float`, `cameraRotY: Float`, `cameraDistance: Float`, `cameraPanX: Float`, `cameraPanY: Float`
    - Add computation state field: `isComputing: Boolean`
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 1.2 Create `shouldFallbackToSinglePane` utility function
    - Add a top-level or companion-object function: `fun shouldFallbackToSinglePane(availableWidthDp: Float, dividerWidthDp: Float): Boolean`
    - Return `true` if `(availableWidthDp - dividerWidthDp) / 2f < 200f`
    - Place in `MainViewModel.kt` or a dedicated `LayoutUtils.kt` file
    - _Requirements: 1.5, 2.5_

- [x] 2. Create tablet layout XML files
  - [x] 2.1 Create tablet landscape layout (`layout-sw600dp-land/activity_main.xml`)
    - Create directory `app/src/main/res/layout-sw600dp-land/`
    - Create `activity_main.xml` with a horizontal `LinearLayout` split
    - Left pane: Code_Editor_Pane with `layout_weight="1"` containing `lineNumbers` and `codeEditor` inside ScrollView/HorizontalScrollView
    - Vertical `paneDivider` View (id: `@+id/paneDivider`, width: 2dp, dark background)
    - Right pane: Preview_Pane with `layout_weight="1"` containing `previewContainer`, `previewPlaceholder`, `progressBar`, view controls overlay include, cancel button, and console container
    - No `TabLayout`, no `ViewFlipper`
    - Button bar at the bottom spanning full width
    - Status bar below button bar
    - Use identical view IDs as the phone layout for shared binding in MainActivity
    - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 7.1_

  - [x] 2.2 Create tablet portrait layout (`layout-sw600dp-port/activity_main.xml`)
    - Create directory `app/src/main/res/layout-sw600dp-port/`
    - Create `activity_main.xml` with a vertical `LinearLayout` split
    - Top pane: Code_Editor_Pane with `layout_weight="1"`
    - Horizontal `paneDivider` View (id: `@+id/paneDivider`, height: 2dp, dark background)
    - Bottom pane: Preview_Pane with `layout_weight="1"`
    - Same view IDs as landscape tablet layout and phone layout
    - No `TabLayout`, no `ViewFlipper`
    - Button bar and status bar at the bottom spanning full width
    - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 7.2_

- [x] 3. Modify MainActivity for tablet/phone conditional setup
  - [x] 3.1 Add tablet detection and MainViewModel integration
    - Add `private val isTabletLayout: Boolean by lazy { findViewById<View>(R.id.paneDivider) != null }` after `setContentView`
    - Obtain `MainViewModel` via `ViewModelProvider(this)[MainViewModel::class.java]`
    - In `initViews()`, conditionally find `tabLayout` and `viewFlipper` only when `!isTabletLayout`
    - On tablet: skip TabLayout/ViewFlipper setup, directly bind both panes as always-visible
    - _Requirements: 1.1, 1.2, 1.3, 2.4_

  - [x] 3.2 Implement state save/restore with MainViewModel
    - In `onPause()` or a dedicated `saveStateToViewModel()`: push `codeEditor.text`, `codeEditor.selectionStart`, `currentFileName`, `statusBar.text`, mesh data arrays, camera state, and compute state to MainViewModel
    - In `onCreate()` after view init: restore state from MainViewModel if non-default (editorText is not empty)
    - Clamp cursor position to `min(viewModel.cursorPosition, restoredText.length)` to prevent IndexOutOfBoundsException
    - On restore with mesh data: recreate GLSurfaceView and apply mesh + camera state
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 3.3 Implement fallback logic for narrow panes
    - After `setContentView` and `isTabletLayout` detection, measure available width in dp
    - Call `shouldFallbackToSinglePane(availableWidthDp, dividerWidthDp)`
    - If true: hide one pane, show TabLayout (inflate phone behavior dynamically or hide split pane children)
    - _Requirements: 1.5, 2.5_

  - [x] 3.4 Update `generatePreview()` for tablet mode
    - On tablet: skip `tabLayout.getTabAt(1)?.select()` since both panes are always visible
    - Ensure console container visibility logic works with tablet (no tab position check on tablet)
    - _Requirements: 5.3_

  - [x] 3.5 Update `updateViewControlsVisibility()` for tablet mode
    - Change condition from `currentTabPosition == 1` to `isTabletLayout || currentTabPosition == 1`
    - View controls overlay visible when `(isTabletLayout || currentTabPosition == 1) && currentMesh != null`
    - _Requirements: 4.2_

  - [x] 3.6 Handle computation cancellation on config change
    - In `onDestroy()` or `onSaveInstanceState()`: if `computeJob?.isActive == true`, cancel computation
    - Set `viewModel.isComputing = false` and store "Computation cancelled" as status text
    - _Requirements: 6.5_

- [x] 4. Checkpoint - Verify layouts inflate and basic tablet flow works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Property-based tests
  - [x] 5.1 Write property test for layout fallback decision (Property 1)
    - **Property 1: Layout fallback decision correctness**
    - Create `app/src/test/java/com/openscadviewer/LayoutDecisionPropertyTest.kt`
    - Generate random `availableWidthDp` (0–2000dp) and `dividerWidthDp` (0–10dp) using jqwik `@ForAll`
    - Assert `shouldFallbackToSinglePane(w, d)` returns `true` iff `(w - d) / 2 < 200`
    - Minimum 100 tries
    - **Validates: Requirements 1.5, 2.5**

  - [x] 5.2 Write property test for UI state round-trip (Property 2)
    - **Property 2: UI state preservation round-trip**
    - Create `app/src/test/java/com/openscadviewer/MainViewModelStatePropertyTest.kt`
    - Generate random strings (0–10000 chars) for `editorText`, random ints for `cursorPosition` (clamped to text length), random strings for `fileName` and `statusBarText`
    - Store into MainViewModel fields, read back, assert equality
    - Minimum 100 tries
    - **Validates: Requirements 6.1, 6.2, 1.4, 7.3**

  - [x] 5.3 Write property test for renderer state round-trip (Property 3)
    - **Property 3: Renderer state preservation round-trip**
    - Create `app/src/test/java/com/openscadviewer/MainViewModelRendererPropertyTest.kt`
    - Generate random `FloatArray` for vertices (length multiple of 3), normals (length multiple of 3), colors (length multiple of 4)
    - Generate random finite floats for camera parameters (distance > 0)
    - Store into MainViewModel, read back, assert byte-for-byte identical arrays and equal floats
    - Minimum 100 tries
    - **Validates: Requirements 6.3, 6.4, 1.4, 7.3**

  - [x] 5.4 Write property test for view controls visibility (Property 4)
    - **Property 4: View controls visibility logic**
    - Create `app/src/test/java/com/openscadviewer/ViewControlsVisibilityPropertyTest.kt`
    - Exhaustively test all combinations: `isTabletLayout` ∈ {true, false}, `currentTabPosition` ∈ {0, 1}, `hasMesh` ∈ {true, false}
    - Assert visibility is `VISIBLE` iff `(isTabletLayout || currentTabPosition == 1) && hasMesh`
    - **Validates: Requirements 4.2**

- [x] 6. Unit and integration tests
  - [x] 6.1 Write unit tests for MainViewModel state operations
    - Test storing and retrieving editor text, cursor position, file name, status text
    - Test cursor clamping when restored text is shorter than stored cursor position
    - Test default values on fresh ViewModel instance
    - _Requirements: 6.1, 6.2_

  - [x] 6.2 Write unit tests for tablet detection and generatePreview behavior
    - Test `isTabletLayout` returns true when `paneDivider` is present
    - Test `isTabletLayout` returns false when `paneDivider` is absent
    - Test `generatePreview()` does not attempt tab switching on tablet mode
    - Test computation cancellation on config change sets "Computation cancelled" status
    - _Requirements: 1.1, 1.2, 5.3, 6.5_

  - [x] 6.3 Write integration tests for layout inflation with Robolectric qualifiers
    - Test correct layout inflated on sw ≥ 600dp device (paneDivider present)
    - Test correct layout inflated on sw < 600dp device (paneDivider absent)
    - Test tablet landscape arrangement is horizontal
    - Test tablet portrait arrangement is vertical
    - Test button bar is present and spans full width in tablet mode
    - _Requirements: 1.1, 1.2, 1.3, 7.1, 7.2, 5.1_

- [x] 7. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses `net.jqwik:jqwik:1.8.4` for property-based testing (already in dependencies)
- All Kotlin code follows existing package conventions under `com.openscadviewer`
- Layout XMLs must reuse the same view IDs as the phone layout to keep MainActivity binding code shared
- The `shouldFallbackToSinglePane` function is extracted as a pure function for easy property testing

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["3.1", "3.2"] },
    { "id": 3, "tasks": ["3.3", "3.4", "3.5", "3.6"] },
    { "id": 4, "tasks": ["5.1", "5.4"] },
    { "id": 5, "tasks": ["5.2", "5.3", "6.1", "6.2"] },
    { "id": 6, "tasks": ["6.3"] }
  ]
}
```

# Requirements Document

## Introduction

This feature adds a tablet-optimized layout to the Pncad (OpenSCAD Viewer) Android app. Currently, the app uses a phone-oriented single-pane layout where the code editor and 3D preview are shown in separate tabs via a ViewFlipper. On tablets (devices with a smallest width of 600dp or greater), the app will display both panes simultaneously in a side-by-side split layout, eliminating the need for tab switching and enabling a more productive editing workflow.

## Glossary

- **Layout_Manager**: The component responsible for selecting and inflating the appropriate layout resource based on the current device screen size.
- **Split_Pane_Layout**: A side-by-side arrangement where the Code_Editor_Pane occupies the left portion and the Preview_Pane occupies the right portion of the screen.
- **Code_Editor_Pane**: The panel containing the line numbers and code EditText with syntax highlighting.
- **Preview_Pane**: The panel containing the GLSurfaceView, preview placeholder, progress indicators, console overlay, and view controls.
- **Tablet_Device**: A device whose smallest screen width is 600dp or greater.
- **Phone_Device**: A device whose smallest screen width is less than 600dp.
- **Tab_Navigation**: The TabLayout and ViewFlipper mechanism used on Phone_Device to switch between Code_Editor_Pane and Preview_Pane.
- **Pane_Divider**: A visual separator between the Code_Editor_Pane and Preview_Pane in the Split_Pane_Layout.
- **MainActivity**: The single activity hosting the editor and preview UI.

## Requirements

### Requirement 1: Screen Size Detection and Layout Selection

**User Story:** As a user, I want the app to automatically detect whether my device is a tablet or phone, so that it presents the most appropriate layout without manual configuration.

#### Acceptance Criteria

1. WHEN the app launches on a Tablet_Device, THE Layout_Manager SHALL inflate the Split_Pane_Layout showing both Code_Editor_Pane and Preview_Pane simultaneously, with each pane occupying a minimum of 30% and a maximum of 70% of the available horizontal width.
2. WHEN the app launches on a Phone_Device, THE Layout_Manager SHALL inflate the existing single-pane layout with Tab_Navigation.
3. THE Layout_Manager SHALL use Android resource qualifiers (smallest width 600dp) to determine which layout to inflate.
4. WHEN the device configuration changes (e.g., screen rotation), THE Layout_Manager SHALL re-evaluate the layout selection and inflate the appropriate layout for the new configuration within 1 second of the configuration change completing, while preserving user state including editor text content, cursor position, and preview state.
5. IF the Layout_Manager inflates the Split_Pane_Layout and the available width for either pane falls below 200dp, THEN THE Layout_Manager SHALL fall back to the single-pane layout with Tab_Navigation.

### Requirement 2: Split Pane Layout Structure

**User Story:** As a user on a tablet, I want to see both the code editor and 3D preview side by side, so that I can edit code and observe the rendered result without switching tabs.

#### Acceptance Criteria

1. WHILE the Split_Pane_Layout is active on devices with a smallest-width of 600dp or greater, THE Code_Editor_Pane SHALL be displayed on the left side and THE Preview_Pane SHALL be displayed on the right side, together filling the full available width between screen edges.
2. THE Split_Pane_Layout SHALL allocate 50% of the available width (±2%) to each pane as the default proportion, excluding the width consumed by the Pane_Divider.
3. THE Split_Pane_Layout SHALL display a Pane_Divider between the Code_Editor_Pane and Preview_Pane as a vertical line with a width between 1dp and 4dp.
4. WHILE the Split_Pane_Layout is active, THE TabLayout and ViewFlipper SHALL not be displayed because both panes are already simultaneously visible.
5. WHILE the Split_Pane_Layout is active, each pane SHALL maintain a minimum width of 200dp to ensure content remains usable.

### Requirement 3: Code Editor Pane in Tablet Mode

**User Story:** As a user on a tablet, I want the code editor to function identically to the phone layout, so that I can edit OpenSCAD code with full syntax highlighting and line numbers.

#### Acceptance Criteria

1. WHILE the Split_Pane_Layout is active, THE Code_Editor_Pane SHALL display the line numbers TextView and the code EditText with syntax highlighting applied by the SyntaxHighlighter class.
2. WHILE the Split_Pane_Layout is active, THE Code_Editor_Pane SHALL support vertical and horizontal scrolling for long code files, with the line numbers TextView scrolling vertically in sync with the code EditText.
3. WHILE the Split_Pane_Layout is active, THE Code_Editor_Pane SHALL use the same monospace font family at 13sp text size with the same color scheme as the phone layout.
4. WHILE the Split_Pane_Layout is active, THE Code_Editor_Pane SHALL remain fully editable and accept keyboard input.

### Requirement 4: Preview Pane in Tablet Mode

**User Story:** As a user on a tablet, I want the 3D preview to function identically to the phone layout, so that I can view rendered models, interact with camera controls, and access the console overlay.

#### Acceptance Criteria

1. WHILE the Split_Pane_Layout is active, THE Preview_Pane SHALL display the GLSurfaceView, preview placeholder, progress bar, and cancel button, each sized to fill the Preview_Pane's available width and height.
2. WHILE the Split_Pane_Layout is active, IF a mesh is loaded, THEN THE Preview_Pane SHALL display the view controls overlay (Top, Front, Left, Right camera snap buttons) positioned within the Preview_Pane bounds, and SHALL hide the view controls overlay when no mesh is loaded.
3. WHILE the Split_Pane_Layout is active, THE Preview_Pane SHALL display the console overlay within its own bounds, providing log entry display via the RecyclerView, a scroll-to-bottom button, a copy button that copies all log text to the clipboard, and a close button that hides the console overlay.
4. WHILE the Split_Pane_Layout is active, THE Preview_Pane SHALL handle touch-based camera rotation, zooming, and panning such that touch events originating within the Preview_Pane are consumed by the Preview_Pane and do not propagate to the Code_Editor_Pane.
5. WHILE the Split_Pane_Layout is active, WHEN the user taps a camera snap button (Top, Front, Left, or Right), THE Preview_Pane SHALL reposition the camera to the corresponding orthogonal view and request a re-render of the GLSurfaceView.

### Requirement 5: Button Bar Adaptation

**User Story:** As a user on a tablet, I want the action buttons (Open File, Preview, Render) to remain accessible, so that I can trigger file operations and rendering from the split layout.

#### Acceptance Criteria

1. WHILE the Split_Pane_Layout is active, THE MainActivity SHALL display the button bar (Open File, Preview, Render) constrained to the bottom of the screen, spanning from the start edge to the end edge of the parent layout below both panes.
2. WHILE the Split_Pane_Layout is active, THE button bar SHALL display all three MaterialButtons (Open File, Preview, Render STL) with equal width distribution, the same outlined/filled styles, and the same enabled/disabled states as the phone layout.
3. WHILE the Split_Pane_Layout is active, WHEN the user taps Preview, THE Preview_Pane SHALL initiate model rendering in place without switching the ViewFlipper or changing pane visibility.
4. WHILE the Split_Pane_Layout is active, WHEN the user taps Open File or Render STL, THE MainActivity SHALL execute the same file-picker or STL-export operation as the phone layout without altering pane visibility.

### Requirement 6: State Preservation Across Configuration Changes

**User Story:** As a user, I want my editor content and preview state to be preserved when rotating my tablet, so that I do not lose work during layout changes.

#### Acceptance Criteria

1. WHEN a configuration change occurs on a Tablet_Device, THE MainActivity SHALL preserve the code editor text content and cursor position using onSaveInstanceState/onRestoreInstanceState or a ViewModel.
2. WHEN a configuration change occurs on a Tablet_Device, THE MainActivity SHALL preserve the current file name and status bar text.
3. WHEN a configuration change occurs on a Tablet_Device, THE MainActivity SHALL preserve the rendered mesh data (vertices, normals, colors) and restore the GLSurfaceView with the previously computed geometry after the configuration change completes.
4. WHEN a configuration change occurs on a Tablet_Device, THE MainActivity SHALL preserve the camera state (rotation, zoom, and pan values) and restore them after the layout is re-inflated.
5. IF a computation is in progress during a configuration change, THEN THE MainActivity SHALL cancel the computation and display "Computation cancelled" in the status bar.

### Requirement 7: Landscape and Portrait Orientation on Tablets

**User Story:** As a tablet user, I want the split layout to work in both landscape and portrait orientations, so that I can hold the device in any orientation while using the dual-pane view.

#### Acceptance Criteria

1. WHILE the Tablet_Device is in landscape orientation and the Split_Pane_Layout is active, THE Split_Pane_Layout SHALL arrange panes horizontally (left: Code_Editor_Pane, right: Preview_Pane) with a vertical Pane_Divider, each pane occupying 50% of the available width.
2. WHILE the Tablet_Device is in portrait orientation and the Split_Pane_Layout is active, THE Split_Pane_Layout SHALL arrange panes vertically (top: Code_Editor_Pane, bottom: Preview_Pane) with a horizontal Pane_Divider, each pane occupying 50% of the available height.
3. WHEN the Tablet_Device rotates between landscape and portrait, THE Layout_Manager SHALL re-arrange the panes to match the new orientation while preserving editor and preview state as specified in Requirement 6.
4. WHEN the Tablet_Device rotates between landscape and portrait, THE Layout_Manager SHALL complete the layout transition within 1 second of the system delivering the new configuration.

# Requirements Document

## Introduction

This feature adds comprehensive file management to the OpenSCAD Viewer Android application. It replaces the current single-file workflow with a multi-file model, providing Open, Save, Save As, and Close operations accessible from a new icon bar. The app persists the last opened file and re-opens it on startup. Users can work with multiple files simultaneously and switch between them via a dedicated open-files list.

## Glossary

- **App**: The OpenSCAD Viewer Android application
- **File_Bar**: A horizontal icon bar positioned at the top of the screen (below or replacing the existing MaterialToolbar) containing expandable icon menus
- **File_Menu**: An expandable panel triggered by a "File" icon in the File_Bar, showing icons for Open, Save, Save As, and Close operations
- **Open_Files_Menu**: An expandable panel triggered by a "Files" icon in the File_Bar, displaying a scrollable list of currently open files
- **Active_File**: The file whose content is currently displayed in the code editor and is the target of Save operations
- **File_Session**: The in-memory representation of an open file, including its URI, display name, content, and dirty state
- **Dirty_State**: A boolean flag indicating that a file's editor content differs from its last saved version
- **SharedPreferences**: The Android key-value storage used to persist the last opened file URI across app launches

## Requirements

### Requirement 1: File Bar Display

**User Story:** As a user, I want a persistent icon bar at the top of the screen, so that I can quickly access file operations and the open-files list.

#### Acceptance Criteria

1. THE File_Bar SHALL display horizontally below the existing MaterialToolbar and above the TabLayout
2. THE File_Bar SHALL contain a "File" icon and an "Open Files" icon
3. THE File_Bar SHALL remain visible regardless of which tab (Code or 3D Preview) is active
4. WHEN the user taps the "File" icon, THE File_Bar SHALL expand the File_Menu below the icon
5. WHEN the user taps the "Open Files" icon, THE File_Bar SHALL expand the Open_Files_Menu below the icon
6. WHEN one menu is open and the user taps the other menu icon, THE File_Bar SHALL close the first menu and open the second menu
7. WHEN the user taps outside an open menu, THE File_Bar SHALL close the open menu
8. WHEN the user taps the same icon that opened the currently expanded menu, THE File_Bar SHALL close that menu (toggle behavior)
9. WHEN the App launches, THE File_Bar SHALL display with all menus in the collapsed state

### Requirement 2: Open File Operation

**User Story:** As a user, I want to open .scad files from device storage, so that I can view and edit them in the app.

#### Acceptance Criteria

1. WHEN the user taps the Open icon in the File_Menu, THE App SHALL launch the Android document picker filtered to all file types
2. WHEN the user selects a file from the document picker, THE App SHALL read the file content as UTF-8 text via ContentResolver and create a new File_Session
3. WHEN the user selects a file that is already open (matching by URI), THE App SHALL switch to the existing File_Session for that file instead of creating a duplicate
4. WHEN a new File_Session is created, THE App SHALL set the new file as the Active_File and display its content in the code editor
5. WHEN a new File_Session is created, THE App SHALL take a persistable URI permission for the selected file
6. IF the file content cannot be read, THEN THE App SHALL display an error message in a Snackbar indicating the file could not be opened, and retain the current Active_File unchanged
7. IF the user dismisses the document picker without selecting a file, THEN THE App SHALL retain the current Active_File and File_Sessions unchanged

### Requirement 3: Save File Operation

**User Story:** As a user, I want to save my edits to the current file, so that changes are persisted to storage.

#### Acceptance Criteria

1. WHEN the user taps the Save icon in the File_Menu, IF the Active_File has a URI, THEN THE App SHALL write the full editor content to the Active_File URI via ContentResolver
2. WHEN the save operation completes successfully, THE App SHALL clear the Dirty_State of the Active_File
3. WHEN the save operation completes successfully, THE App SHALL display a message in the status bar indicating the file was saved, including the file name, and the message SHALL remain visible until replaced by another status update or for at least 3 seconds
4. IF the save operation fails due to an I/O error or unavailable URI, THEN THE App SHALL display an error message in a Snackbar for at least 4 seconds indicating the failure reason, and SHALL retain the Dirty_State and the editor content unchanged
5. WHEN the user taps the Save icon in the File_Menu, IF the Active_File has no URI, THEN THE App SHALL launch the Save As flow (ACTION_CREATE_DOCUMENT)
6. WHILE a save operation is in progress, THE App SHALL ignore additional taps on the Save icon until the current save completes or fails

### Requirement 4: Save As Operation

**User Story:** As a user, I want to save the current file to a new location, so that I can create copies or save new files.

#### Acceptance Criteria

1. WHEN the user taps the Save As icon in the File_Menu, THE App SHALL launch the Android document creator with a suggested filename matching the Active_File display name
2. WHEN the user confirms a destination in the document creator, THE App SHALL write the editor content as UTF-8 text to the selected URI via ContentResolver
3. WHEN the Save As operation completes successfully, THE App SHALL update the Active_File URI and display name to reflect the new location
4. WHEN the Save As operation completes successfully, THE App SHALL clear the Dirty_State of the Active_File
5. IF the Save As operation fails, THEN THE App SHALL display an error message in a Snackbar for at least 4 seconds indicating the failure reason, retain the previous Active_File URI, and retain the Dirty_State and editor content unchanged
6. IF the user dismisses the document creator without confirming a destination, THEN THE App SHALL retain the current Active_File URI, Dirty_State, and editor content unchanged

### Requirement 5: Close File Operation

**User Story:** As a user, I want to close a file I no longer need, so that the open-files list stays manageable.

#### Acceptance Criteria

1. WHEN the user taps the Close icon in the File_Menu, THE App SHALL remove the Active_File from the list of open File_Sessions
2. WHEN the Active_File is closed and other File_Sessions remain open, THE App SHALL set the most recently accessed remaining File_Session as the new Active_File
3. WHEN the Active_File is closed and no other File_Sessions remain, THE App SHALL display an empty editor with non-editable placeholder text
4. IF the user attempts to close a file with Dirty_State set to true, THEN THE App SHALL display a confirmation dialog presenting exactly three options: save, discard, and cancel
5. WHEN the user selects "Save" in the close confirmation dialog, THE App SHALL perform the Save operation and, only upon successful save completion, close the file
6. IF the Save operation initiated from the close confirmation dialog fails, THEN THE App SHALL retain the file as Active_File with Dirty_State unchanged, dismiss the confirmation dialog, and display an error message indicating the save failure reason
7. WHEN the user selects "Discard" in the close confirmation dialog, THE App SHALL close the file without saving and discard all unsaved changes
8. WHEN the user selects "Cancel" in the close confirmation dialog, THE App SHALL retain the file as Active_File with Dirty_State unchanged and dismiss the dialog

### Requirement 6: Multi-File Session Management

**User Story:** As a user, I want to have multiple files open simultaneously, so that I can switch between them without re-opening from storage.

#### Acceptance Criteria

1. THE App SHALL maintain an ordered list of open File_Sessions in memory, ordered by most-recently-accessed first
2. WHEN the user switches between files, THE App SHALL preserve each File_Session content, cursor position, and Dirty_State independently
3. THE Open_Files_Menu SHALL display the display name of each open File_Session in a vertically scrollable list ordered by most-recently-accessed first
4. THE Open_Files_Menu SHALL visually distinguish the Active_File entry from other entries using a highlighted background color
5. WHEN the user taps a file entry in the Open_Files_Menu, THE App SHALL set that file as the Active_File and display its content in the code editor
6. THE Open_Files_Menu SHALL display a Dirty_State indicator (asterisk prefix) next to files with unsaved changes
7. THE App SHALL support a maximum of 20 simultaneously open File_Sessions
8. IF the user attempts to open a file when 20 File_Sessions are already open, THEN THE App SHALL display a Snackbar message indicating the maximum has been reached

### Requirement 7: Last File Persistence

**User Story:** As a user, I want the app to remember the last file I was working on, so that I can resume where I left off after restarting.

#### Acceptance Criteria

1. WHEN a file becomes the Active_File, THE App SHALL persist the file URI as a string to SharedPreferences before any subsequent user interaction is processed
2. WHEN the App launches with no incoming intent and a persisted file URI exists in SharedPreferences, THE App SHALL open the persisted file, load its content into the editor, and set it as the Active_File
3. IF the persisted file URI cannot be opened via ContentResolver on launch (due to file deletion, permission revocation, or any other access failure), THEN THE App SHALL clear the persisted URI from SharedPreferences and display the empty editor
4. WHEN the App launches with an incoming intent containing a file URI, THE App SHALL open the intent URI and set it as the Active_File regardless of any persisted URI in SharedPreferences
5. WHEN the App launches with no incoming intent and no persisted file URI exists in SharedPreferences, THE App SHALL display the empty editor

### Requirement 8: Dirty State Tracking

**User Story:** As a user, I want to know which files have unsaved changes, so that I avoid losing work.

#### Acceptance Criteria

1. WHEN the editor content of the Active_File changes from its last saved state, THE App SHALL set the Dirty_State of that File_Session to true
2. WHEN the editor content of the Active_File matches its last saved state, THE App SHALL set the Dirty_State of that File_Session to false
3. WHILE a File_Session has Dirty_State set to true, THE App SHALL display an asterisk (*) character prepended to the file name in the status bar for the Active_File
4. WHILE a File_Session has Dirty_State set to true, THE App SHALL display an asterisk (*) character prepended to the file name in the Open_Files_Menu entry for that File_Session
5. WHEN the user saves a file, THE App SHALL update the last saved state to the current editor content and re-evaluate the Dirty_State for that File_Session
6. WHEN a new file is created that has not been saved to disk, THE App SHALL set the Dirty_State of that File_Session to true immediately
7. WHEN a file is opened from disk, THE App SHALL set the Dirty_State of that File_Session to false and record the file content as the last saved state

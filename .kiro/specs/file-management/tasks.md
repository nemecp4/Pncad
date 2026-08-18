# Implementation Plan: File Management

## Overview

This plan implements multi-file session management for the OpenSCAD Viewer app. The approach is bottom-up: first build the data model and session logic (independently testable), then the ViewModel, then the UI layer (File Bar, menus), and finally wire everything together with the existing MainActivity.

## Tasks

- [x] 1. Create data model and session manager
  - [x] 1.1 Create FileSession data class and Event utility
    - Create `app/src/main/java/com/openscadviewer/file/FileSession.kt` with the `FileSession` data class (id, uri, displayName, content, lastSavedContent, cursorPosition, lastAccessedTimestamp, isDirty computed property)
    - Create `app/src/main/java/com/openscadviewer/file/Event.kt` with the single-shot `Event<T>` wrapper class
    - Create `app/src/main/java/com/openscadviewer/file/CloseDialogChoice.kt` enum (SAVE, DISCARD, CANCEL)
    - _Requirements: 6.1, 6.2, 8.1, 8.2_

  - [x] 1.2 Implement FileSessionManager
    - Create `app/src/main/java/com/openscadviewer/file/FileSessionManager.kt`
    - Implement session list management: `getOrderedSessions()`, `getActiveSession()`
    - Implement `openFile()` — adds new session or reuses existing (by URI match), enforces max 20 cap, moves to MRU position
    - Implement `switchTo()` — updates lastAccessedTimestamp and reorders
    - Implement `updateContent()` — sets content and cursorPosition on a session
    - Implement `markSaved()` — updates lastSavedContent, optionally updates uri/displayName
    - Implement `closeSession()` — removes session, returns new active or null
    - Implement `findByUri()`, `createUntitled()`
    - Implement persistence: `persistActiveUri()`, `getPersistedUri()`, `clearPersistedUri()` using SharedPreferences
    - _Requirements: 2.2, 2.3, 3.2, 4.3, 4.4, 5.1, 5.2, 6.1, 6.2, 6.7, 7.1, 8.5, 8.6, 8.7_

  - [x] 1.3 Write property tests for FileSessionManager
    - **Property 1: Open file creates active session**
    - **Property 2: Opening duplicate URI reuses existing session**
    - **Property 4: Save clears dirty state**
    - **Property 5: Save-as updates session identity**
    - **Property 6: Close removes session and promotes next MRU**
    - **Property 7: MRU ordering invariant**
    - **Property 9: Session count capped at 20**
    - **Property 10: Dirty state reflects content divergence**
    - **Property 11: Initial dirty state correctness**
    - Create `app/src/test/kotlin/com/openscadviewer/file/FileSessionManagerPropertyTest.kt`
    - Use jqwik with custom generators for file content, URIs, display names, and operation sequences
    - **Validates: Requirements 2.2, 2.3, 3.2, 4.3, 4.4, 5.1, 5.2, 6.1, 6.7, 8.1, 8.2, 8.5, 8.6, 8.7**

  - [x] 1.4 Write property test for session state isolation
    - **Property 8: Session state isolation on switch**
    - Create or extend `app/src/test/kotlin/com/openscadviewer/file/FileSessionManagerPropertyTest.kt`
    - Verify switching between sessions preserves content, cursor, and dirty state
    - **Validates: Requirements 6.2**

  - [x] 1.5 Write property test for error resilience
    - **Property 3: Failed I/O preserves session state**
    - Test that session list, active pointer, content, and dirty state remain unchanged after simulated I/O failure
    - **Validates: Requirements 2.6, 3.4, 4.5, 5.6**

- [x] 2. Implement FileContentReader utility
  - [x] 2.1 Create FileContentReader object
    - Create `app/src/main/java/com/openscadviewer/file/FileContentReader.kt`
    - Implement `readContent(contentResolver, uri): Result<String>` — opens InputStream, reads UTF-8, catches IOException/SecurityException
    - Implement `writeContent(contentResolver, uri, content): Result<Unit>` — opens OutputStream with "wt" mode, writes UTF-8, catches IOException/SecurityException
    - Implement `getDisplayName(contentResolver, uri): String` — queries DISPLAY_NAME column from content provider
    - _Requirements: 2.2, 3.1, 4.2_

  - [x] 2.2 Write unit tests for FileContentReader
    - Test readContent with mock ContentResolver returning valid/null/exception
    - Test writeContent with mock ContentResolver returning valid/null/exception
    - Test getDisplayName with cursor returning name vs null cursor
    - _Requirements: 2.6, 3.4, 4.5_

- [x] 3. Checkpoint - Core logic complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement FileViewModel
  - [x] 4.1 Create FileViewModel with LiveData state
    - Create `app/src/main/java/com/openscadviewer/file/FileViewModel.kt`
    - Extend `AndroidViewModel` with Application context for ContentResolver access
    - Instantiate `FileSessionManager` with default SharedPreferences
    - Expose `activeSession: LiveData<FileSession?>`, `sessions: LiveData<List<FileSession>>`, `statusMessage: LiveData<String>`, `errorEvent: LiveData<Event<String>>`
    - Expose navigation events: `openPickerEvent: LiveData<Event<Unit>>`, `saveAsEvent: LiveData<Event<String>>` (suggested filename)
    - _Requirements: 6.1, 6.2_

  - [x] 4.2 Implement file operations in FileViewModel
    - Implement `handleFileSelected(uri: Uri)` — take persistable permission, read content via FileContentReader, call sessionManager.openFile() or switch if duplicate, update LiveData
    - Implement `save()` — if active has URI write via FileContentReader and markSaved, else trigger saveAs event; include concurrency guard (ignore while saving)
    - Implement `handleSaveAsDestination(uri: Uri)` — take persistable permission, write content, markSaved with new URI/name, update LiveData
    - Implement `closeActiveFile()` / `closeWithConfirmation()` — check dirty, emit close dialog event or close directly
    - Implement `confirmClose(choice: CloseDialogChoice)` — handle save/discard/cancel
    - Implement `switchToFile(sessionId: String)` — call sessionManager.switchTo(), update LiveData
    - Implement `onEditorContentChanged(content, cursorPosition)` — call sessionManager.updateContent(), update LiveData
    - Implement `restoreOnLaunch(intentUri: Uri?)` — check intent URI, then persisted URI, then empty editor
    - _Requirements: 2.2, 2.3, 2.5, 2.6, 3.1, 3.2, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 4.3 Write unit tests for FileViewModel
    - Test save concurrency guard
    - Test restoreOnLaunch with intent URI, persisted URI, and no URI
    - Test close confirmation flow with all three choices
    - Test error events emitted on I/O failure
    - _Requirements: 3.6, 5.4, 5.5, 5.6, 7.2, 7.3, 7.4, 7.5_

- [x] 5. Implement File Bar UI components
  - [x] 5.1 Create File Bar layout and custom view
    - Create `app/src/main/res/layout/file_bar.xml` — horizontal LinearLayout with two ImageButtons (file icon, open-files icon)
    - Add drawable resources for file menu icon and open-files icon (use existing Material icons or Android system icons as placeholders)
    - Include the File Bar in `activity_main.xml` between toolbar and tabLayout, update constraints accordingly
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 5.2 Create File Menu popup
    - Create `app/src/main/res/layout/popup_file_menu.xml` — vertical LinearLayout with four icon buttons: Open, Save, Save As, Close
    - Implement `FileMenuPopup.kt` in `com.openscadviewer.file` — uses PopupWindow, positions below trigger icon, handles click listeners that delegate to FileViewModel
    - _Requirements: 1.4, 1.6, 1.7, 1.8_

  - [x] 5.3 Create Open Files Menu popup
    - Create `app/src/main/res/layout/popup_open_files_menu.xml` — RecyclerView inside a PopupWindow
    - Create `app/src/main/res/layout/item_open_file.xml` — single row: text with dirty indicator and highlighted background for active
    - Implement `OpenFilesAdapter.kt` — RecyclerView.Adapter displaying session list with dirty asterisk and active highlight
    - Implement `OpenFilesMenuPopup.kt` — uses PopupWindow with RecyclerView, positions below trigger icon, click delegates to FileViewModel.switchToFile()
    - _Requirements: 1.5, 6.3, 6.4, 6.5, 6.6_

  - [x] 5.4 Implement File Bar toggle and mutual exclusion logic
    - In `FileBarController.kt` (or within MainActivity setup), implement toggle behavior: tapping same icon closes its menu, tapping other icon closes current and opens other, tapping outside closes any open menu
    - _Requirements: 1.6, 1.7, 1.8, 1.9_

- [x] 6. Checkpoint - UI components built
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Integrate with MainActivity
  - [x] 7.1 Wire FileViewModel into MainActivity
    - Obtain `FileViewModel` via `ViewModelProvider` in `onCreate`
    - Observe `activeSession` LiveData — update code editor text, cursor position, status bar (with dirty indicator prefix)
    - Observe `sessions` LiveData — update OpenFilesAdapter data
    - Observe `statusMessage` — update status bar text
    - Observe `errorEvent` — show Snackbar with error message
    - Observe navigation events — launch SAF intents for open/save-as
    - _Requirements: 2.4, 3.3, 4.1, 5.3, 8.3, 8.4_

  - [x] 7.2 Connect editor changes to FileViewModel
    - Add TextWatcher on codeEditor that calls `fileViewModel.onEditorContentChanged()` with debounce (300ms)
    - On active session change, temporarily disable TextWatcher while loading new content to avoid false dirty triggers
    - _Requirements: 8.1, 8.2, 8.5_

  - [x] 7.3 Handle SAF activity results
    - Update `onActivityResult` (or register ActivityResultContracts) to route PICK_SCAD_FILE result to `fileViewModel.handleFileSelected(uri)`
    - Route SAVE_STL_FILE and new SAVE_AS_FILE result codes appropriately
    - Handle `handleSaveAsDestination(uri)` from the create document result
    - Handle cancelled/null results gracefully (no-op per requirements 2.7, 4.6)
    - _Requirements: 2.1, 2.5, 2.7, 4.1, 4.6_

  - [x] 7.4 Implement close confirmation dialog
    - Show MaterialAlertDialog with three buttons (Save, Discard, Cancel) when `fileViewModel` signals dirty close
    - Route dialog choice to `fileViewModel.confirmClose(choice)`
    - _Requirements: 5.4, 5.5, 5.6, 5.7, 5.8_

  - [x] 7.5 Implement launch/restore logic
    - In `onCreate`, call `fileViewModel.restoreOnLaunch(intent?.data)` after ViewModel initialization
    - Handle empty editor state: show placeholder, disable editing
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 7.6 Remove old single-file Open button
    - Remove or repurpose `btnOpenFile` from the bottom button bar since file operations are now in the File Bar
    - Update bottom button bar layout (Preview and Render STL remain)
    - _Requirements: 1.1, 1.2_

- [x] 8. Handle max sessions and edge cases
  - [x] 8.1 Implement max sessions Snackbar
    - In FileViewModel's `handleFileSelected()`, when sessionManager.openFile() returns a max-capacity error, emit errorEvent with "Maximum 20 files open" message
    - Show Snackbar from the errorEvent observer in MainActivity
    - _Requirements: 6.7, 6.8_

  - [x] 8.2 Handle empty editor state
    - When no sessions are open (after last close or fresh launch), display non-editable placeholder text in the code editor
    - Disable Save/Save As/Close icons in File Menu when no active session
    - _Requirements: 5.3, 7.5_

- [x] 9. Final checkpoint - Full integration complete
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation language is Kotlin (matching the existing project and design document)
- File Bar uses PopupWindow for menus to match Material Design dropdown patterns
- FileSessionManager is a plain class (no Android deps) for easy unit testing with jqwik

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "2.2"] },
    { "id": 3, "tasks": ["4.1", "5.1"] },
    { "id": 4, "tasks": ["4.2", "5.2", "5.3"] },
    { "id": 5, "tasks": ["4.3", "5.4"] },
    { "id": 6, "tasks": ["7.1", "7.6"] },
    { "id": 7, "tasks": ["7.2", "7.3", "7.4", "7.5"] },
    { "id": 8, "tasks": ["8.1", "8.2"] }
  ]
}
```

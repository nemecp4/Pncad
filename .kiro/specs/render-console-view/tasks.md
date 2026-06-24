# Implementation Plan: Render Console View

## Overview

This plan implements a live console view overlay for the OpenSCAD Viewer app that displays real-time progress messages, log output, and resource usage during compute engine operations. The implementation proceeds from core data models and logging infrastructure through UI components to final integration with both compute engines.

## Tasks

- [x] 1. Create core console data models and logging infrastructure
  - [x] 1.1 Create LogEntry, LogSeverity, and ProgressCallback
    - Create `app/src/main/java/com/openscadviewer/console/LogSeverity.kt` with INFO, WARN, ERROR enum
    - Create `app/src/main/java/com/openscadviewer/console/LogEntry.kt` data class with timestamp, severity, message fields and `formattedTimestamp()` method (HH:mm:ss.SSS format)
    - Create `app/src/main/java/com/openscadviewer/engine/ProgressCallback.kt` functional interface with `onProgress(message, severity)` method
    - _Requirements: 3.1, 3.2, 7.1_

  - [x] 1.2 Create ConsoleLogger with SharedFlow-based emission
    - Create `app/src/main/java/com/openscadviewer/console/ConsoleLogger.kt`
    - Implement `MutableSharedFlow<LogEntry>` with replay=100 and extraBufferCapacity=50
    - Implement `startSession()` that clears previous entries and emits session-start message
    - Implement `endSession(success)` that emits completion/failure message
    - Implement `emit(severity, message)` that creates timestamped LogEntry and emits via tryEmit
    - Expose `allEntries: List<LogEntry>` for snapshot access and `entries: SharedFlow<LogEntry>` for streaming
    - _Requirements: 5.1, 5.2, 2.1, 2.4_

  - [x] 1.3 Write property tests for ConsoleLogger
    - **Property 1: Log entry ordering preservation**
    - **Property 5: Session lifecycle clears previous and retains current**
    - **Validates: Requirements 1.3, 5.1, 5.2**

  - [x] 1.4 Write property test for LogEntry timestamp format
    - **Property 2: Timestamp format validity**
    - **Validates: Requirements 3.1**

- [x] 2. Implement ResourceMonitor
  - [x] 2.1 Create ResourceMonitor with coroutine-based polling
    - Create `app/src/main/java/com/openscadviewer/console/ResourceMonitor.kt`
    - Implement `start(scope)` that launches a coroutine on `Dispatchers.Default` polling every 1 second
    - Implement memory reading via `Runtime.getRuntime().totalMemory() - freeMemory()`
    - Implement CPU reading via `/proc/self/stat` utime+stime delta calculation
    - Track peak memory across the session
    - Implement `stop()` that cancels the monitor job and emits peak memory as final log entry
    - Emit "Memory: X.X MB | CPU: X%" entries to ConsoleLogger every interval
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 2.2 Write property test for peak memory accuracy
    - **Property 6: Peak memory accuracy**
    - **Validates: Requirements 6.4**

- [x] 3. Update ComputeEngine interface and implementations
  - [x] 3.1 Add ProgressCallback parameter to ComputeEngine interface
    - Modify `app/src/main/java/com/openscadviewer/engine/ComputeEngine.kt` to add optional `progress: ProgressCallback? = null` parameter to `compute()`
    - _Requirements: 7.1, 7.4_

  - [x] 3.2 Update KotlinComputeEngine to emit progress messages
    - Update `KotlinComputeEngine.compute()` to accept `ProgressCallback` parameter
    - Emit "Parsing OpenSCAD source..." at start
    - Emit "Parsed {N} scene nodes" after parse
    - Emit "Computing mesh with Kotlin engine..." before mesh generation
    - Emit "Mesh generated: {N} triangles in {T}ms" on completion
    - Emit error messages with ERROR severity on failure
    - _Requirements: 7.2, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.3 Update CgalComputeEngine to emit progress messages
    - Update `CgalComputeEngine.compute()` to accept `ProgressCallback` parameter
    - Emit progress messages for parse start, compute start, completion
    - Relay native JNI progress messages via the callback
    - Emit error messages with category and details on failure
    - _Requirements: 7.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.4 Write property tests for progress message content
    - **Property 3: Completion message contains required numeric data**
    - **Property 4: Error message contains required details**
    - **Validates: Requirements 2.2, 2.4, 2.5, 2.6**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create ConsoleViewModel for state management
  - [x] 5.1 Create ConsoleViewModel
    - Create `app/src/main/java/com/openscadviewer/console/ConsoleViewModel.kt`
    - Hold references to `ConsoleLogger` and `ResourceMonitor`
    - Expose `logEntries: LiveData<List<LogEntry>>` by collecting SharedFlow and posting to LiveData
    - Expose `isVisible: LiveData<Boolean>` for console visibility state
    - Expose `autoScroll: LiveData<Boolean>` for scroll behavior control
    - Implement `startSession()` that clears logger, starts resource monitor, sets visible=true
    - Implement `endSession(success)` that stops resource monitor and logs completion
    - Implement `hide()` that sets visible=false
    - Implement `setAutoScroll(enabled)` for manual scroll detection
    - Implement `createProgressCallback()` that returns a ProgressCallback forwarding to logger
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 3.5, 4.2, 5.3_

  - [x] 5.2 Write unit tests for ConsoleViewModel state transitions
    - Test startSession sets isVisible=true and clears entries
    - Test endSession(true) keeps console visible (for delayed hiding)
    - Test endSession(false) keeps console visible permanently
    - Test hide() sets isVisible=false
    - Test setAutoScroll toggles correctly
    - _Requirements: 1.4, 1.5, 4.2_

- [x] 6. Create console UI components
  - [x] 6.1 Create item_log_entry.xml layout
    - Create `app/src/main/res/layout/item_log_entry.xml`
    - Include timestamp TextView, severity TextView, and message TextView in horizontal layout
    - Apply monospace font family to all text views
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 6.2 Create ConsoleAdapter RecyclerView adapter
    - Create `app/src/main/java/com/openscadviewer/console/ConsoleAdapter.kt`
    - Extend `ListAdapter<LogEntry, ViewHolder>` with DiffUtil.ItemCallback
    - Bind timestamp via `formattedTimestamp()`, severity name, and message text
    - Apply color based on severity: INFO=#B0BEC5, WARN=#FFB74D, ERROR=#EF5350
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 6.3 Add console container to activity_main.xml layout
    - Add `consoleContainer` LinearLayout inside `previewContainer` FrameLayout with z-order above GLSurfaceView
    - Include header bar with "Console" title, scroll-to-bottom ImageButton, and close ImageButton
    - Include RecyclerView for log entries with layout_weight=1
    - Include resource bar TextView at bottom for memory/CPU display
    - Include cancel MaterialButton below resource bar
    - Set initial visibility to GONE
    - _Requirements: 1.1, 1.2, 4.1, 4.3, 4.4, 5.3, 6.1, 6.2_

- [x] 7. Integrate console into MainActivity
  - [x] 7.1 Wire ConsoleViewModel and UI in MainActivity
    - Obtain `ConsoleViewModel` via ViewModelProvider in `onCreate()`
    - Initialize ConsoleAdapter and attach to RecyclerView in console container
    - Observe `logEntries` LiveData to submit list to adapter
    - Observe `isVisible` LiveData to toggle consoleContainer visibility
    - Observe `autoScroll` LiveData to auto-scroll RecyclerView to last position
    - Wire close button to `consoleViewModel.hide()`
    - Wire scroll-to-bottom button to resume auto-scroll and scroll to end
    - Detect manual scroll-up via RecyclerView.OnScrollListener to pause auto-scroll
    - _Requirements: 1.3, 3.5, 4.1, 4.2, 4.3, 4.4_

  - [x] 7.2 Update generatePreview() and renderAndExportSTL() to use console
    - Call `consoleViewModel.startSession()` at the beginning of each operation
    - Pass `consoleViewModel.createProgressCallback()` to `engineManager.currentEngine.compute(scene, progressCallback)`
    - Call `consoleViewModel.endSession(true)` on success with 2-second delayed hide via `Handler.postDelayed`
    - Call `consoleViewModel.endSession(false)` on failure (console stays visible)
    - On cancellation, emit "Computation cancelled" to logger and call `endSession(false)`
    - Wire cancel button to existing `cancelComputation()` flow
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 5.1, 5.3_

  - [x] 7.3 Handle tab switching and console state persistence
    - Ensure consoleContainer visibility is restored when switching back to Preview tab during active session
    - Observe `isVisible` with the current tab state to avoid showing console on Code tab
    - _Requirements: 5.4_

  - [x] 7.4 Write unit tests for progress callback wiring
    - Test that createProgressCallback correctly maps severity strings to LogSeverity
    - Test that progress messages flow from engine through callback to logger
    - _Requirements: 7.1, 7.5_

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses Kotlin with JUnit5 and jqwik for property-based testing (already configured in build.gradle.kts)
- The ComputeEngine interface change uses a default parameter (`progress: ProgressCallback? = null`) to maintain backward compatibility

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "3.1"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.1", "3.2", "3.3"] },
    { "id": 3, "tasks": ["2.2", "3.4", "5.1"] },
    { "id": 4, "tasks": ["5.2", "6.1"] },
    { "id": 5, "tasks": ["6.2", "6.3"] },
    { "id": 6, "tasks": ["7.1"] },
    { "id": 7, "tasks": ["7.2", "7.3"] },
    { "id": 8, "tasks": ["7.4"] }
  ]
}
```

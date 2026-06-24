# Requirements Document

## Introduction

This feature adds a live console view to the OpenSCAD Viewer app (Pncad) that displays progress messages and log output while the compute engine is generating a preview or rendering an STL export. The console provides real-time feedback about parsing, computation steps, warnings, and errors so the user can monitor what the engine is doing during potentially long-running operations.

## Glossary

- **Console_View**: A scrollable text view displayed in the preview area that shows timestamped log messages during computation
- **Compute_Engine**: The interface (ComputeEngine) responsible for generating mesh data from a scene graph, either via KotlinComputeEngine or CgalComputeEngine
- **Console_Logger**: A component that collects log messages from the computation pipeline and emits them for display in the Console_View
- **Log_Entry**: A single message with a timestamp, severity level, and text content
- **Computation_Session**: The period from when a preview or render operation starts until it completes, fails, or is cancelled

## Requirements

### Requirement 1: Display Console View During Computation

**User Story:** As a user, I want to see a console view with progress messages while a preview or render is running, so that I know what the engine is doing and whether progress is being made.

#### Acceptance Criteria

1. WHEN the user initiates a preview generation, THE Console_View SHALL become visible in the preview area
2. WHEN the user initiates an STL render, THE Console_View SHALL become visible in the preview area
3. WHILE a Computation_Session is active, THE Console_View SHALL display Log_Entry messages as they are emitted
4. WHEN a Computation_Session completes successfully, THE Console_View SHALL remain visible for at least 2 seconds before the 3D preview replaces it
5. WHEN a Computation_Session fails, THE Console_View SHALL remain visible showing all logged messages including the error
6. WHEN a Computation_Session is cancelled, THE Console_View SHALL display a cancellation message and remain visible

### Requirement 2: Console Log Content

**User Story:** As a user, I want the console to show meaningful progress information, so that I can understand what stage the computation is at.

#### Acceptance Criteria

1. WHEN parsing begins, THE Console_Logger SHALL emit a Log_Entry indicating parsing has started
2. WHEN parsing completes, THE Console_Logger SHALL emit a Log_Entry with the number of scene nodes parsed
3. WHEN mesh computation begins, THE Console_Logger SHALL emit a Log_Entry indicating the engine type being used
4. WHEN mesh computation completes, THE Console_Logger SHALL emit a Log_Entry with the triangle count and elapsed time
5. IF a parse error occurs, THEN THE Console_Logger SHALL emit a Log_Entry with the error details and source location
6. IF a computation error occurs, THEN THE Console_Logger SHALL emit a Log_Entry with the error category and message

### Requirement 3: Log Entry Format

**User Story:** As a user, I want console messages to be clearly formatted with timestamps and severity levels, so that I can quickly scan for important information.

#### Acceptance Criteria

1. THE Console_View SHALL display each Log_Entry with a timestamp in HH:mm:ss.SSS format
2. THE Console_View SHALL display each Log_Entry with a severity indicator (INFO, WARN, ERROR)
3. THE Console_View SHALL use distinct text colors for each severity level
4. THE Console_View SHALL use a monospace font for log message display
5. THE Console_View SHALL auto-scroll to the most recent Log_Entry as new entries are added

### Requirement 4: Console View Interaction

**User Story:** As a user, I want to be able to scroll through console output and dismiss it, so that I can review earlier messages or return to the 3D preview.

#### Acceptance Criteria

1. THE Console_View SHALL allow the user to scroll through all Log_Entry messages in the current Computation_Session
2. WHEN the user manually scrolls up in the Console_View, THE Console_View SHALL pause auto-scrolling
3. WHEN the user taps a "scroll to bottom" indicator, THE Console_View SHALL resume auto-scrolling and scroll to the latest Log_Entry
4. WHEN the user taps the close button on the Console_View, THE Console_View SHALL hide and reveal the 3D preview or placeholder beneath

### Requirement 5: Console State Management

**User Story:** As a user, I want the console to start fresh for each computation and not mix logs from different runs.

#### Acceptance Criteria

1. WHEN a new Computation_Session begins, THE Console_Logger SHALL clear all previous Log_Entry messages
2. THE Console_Logger SHALL retain Log_Entry messages from the current Computation_Session until a new session begins
3. WHILE a Computation_Session is active, THE Console_View SHALL display the cancel button below the console output
4. WHEN the app switches to the Code tab and back to the Preview tab during an active Computation_Session, THE Console_View SHALL still be visible with all current Log_Entry messages

### Requirement 6: Resource Usage Display

**User Story:** As a user, I want to see memory and CPU usage during computation, so that I can understand how demanding the model is and whether the device is under stress.

#### Acceptance Criteria

1. WHILE a Computation_Session is active, THE Console_View SHALL display current memory usage in megabytes at the bottom of the console area
2. WHILE a Computation_Session is active, THE Console_View SHALL display current CPU usage as a percentage at the bottom of the console area
3. THE Console_View SHALL update memory and CPU usage values at an interval of 1 second
4. WHEN a Computation_Session ends, THE Console_View SHALL display the peak memory usage reached during the session as a final Log_Entry
5. THE Console_View SHALL obtain memory usage from the Android Runtime memory API
6. THE Console_View SHALL obtain CPU usage from the process CPU time statistics

### Requirement 7: Console Integration with Compute Engine

**User Story:** As a developer, I want the console logging to integrate cleanly with the existing ComputeEngine interface, so that both Kotlin and CGAL engines can emit progress messages.

#### Acceptance Criteria

1. THE Compute_Engine interface SHALL support an optional progress callback for emitting Log_Entry messages during computation
2. THE KotlinComputeEngine SHALL emit progress Log_Entry messages for each major computation step
3. THE CgalComputeEngine SHALL emit progress Log_Entry messages relayed from the native layer via JNI
4. IF the Compute_Engine does not support progress callbacks, THEN THE Console_Logger SHALL emit only the start and completion Log_Entry messages
5. THE Compute_Engine progress callback SHALL be invoked on a background thread and the Console_Logger SHALL relay messages to the main thread for display

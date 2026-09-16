# Requirements Document

## Introduction

This feature replaces the current plain `EditText`-based OpenSCAD code editor in the Pncad Android app with the [sora-editor](https://github.com/Rosemoe/sora-editor) library (`io.github.Rosemoe.sora-editor`), a purpose-built native Android code editor widget. The migration must preserve all existing editor behavior (syntax highlighting, autocompletion, line numbers, file session sync, cursor restore, and feeding source to the parser/engine) while adding capabilities the current editor lacks (undo/redo, search and replace, a real gutter, incremental highlighting, bracket matching, and text scaling).

The app is built entirely on the Android View system (AppCompat + XML layouts + ViewModel/LiveData). There is no Jetpack Compose and no WebView. sora-editor is a native `View`, so it drops into the existing `ViewFlipper` code pane and tablet split-pane layout without a UI framework migration.

## Glossary

- **Editor**: The interactive code-editing widget used to edit OpenSCAD source. After this migration it is an instance of sora-editor's `CodeEditor`, replacing the previous `EditText` (`R.id.codeEditor`).
- **Legacy_Editor**: The pre-migration implementation: the `EditText` (`R.id.codeEditor`) plus the `lineNumbers` `TextView`, `SyntaxHighlighter`, `CompletionPopup`, and the stacked `TextWatcher`s.
- **Editor_Adapter**: An abstraction layer that exposes editor operations (get/set text, get/set cursor, content-change callback, enable/disable) to `MainActivity`, backed by the `CodeEditor`.
- **File_Session**: A `FileSession` object owned by `FileViewModel`/`FileSessionManager`, holding a file's `content` and `cursorPosition`; the source of truth for editor content.
- **Content_Sync**: The debounced flow that pushes editor edits to `FileViewModel.onEditorContentChanged(text, cursor)`.
- **OpenSCAD_Language**: The sora-editor `Language` implementation (or configured TextMate grammar) that provides OpenSCAD syntax highlighting and completion.
- **Token_Set**: The OpenSCAD keyword, builtin, and math-function lists defined in `OpenScadTokens` (`KEYWORDS`, `BUILTINS`, `MATH_FUNCTIONS`).
- **Completion_Providers**: The existing completion sources: `DocumentScanner`, `KeywordProvider`, `BuiltinProvider`, `MathProvider`, aggregated by `CompletionEngine`.
- **Color_Scheme**: The sora-editor `EditorColorScheme` (or TextMate theme) mapping syntax categories to colors.

## Requirements

### Requirement 1: sora-editor Dependency Integration

**User Story:** As a developer maintaining Pncad, I want the sora-editor library added to the app module, so that the native code editor widget and its language tooling are available at build time.

#### Acceptance Criteria

1. THE app module (`app/build.gradle.kts`) SHALL declare the sora-editor dependency via the published BOM plus the `editor` module.
2. WHERE TextMate-based highlighting is used, THE app module SHALL additionally declare the `language-textmate` module.
3. THE added dependency versions SHALL be compatible with the project's `minSdk = 26`, `compileSdk = 34`, and JVM 17 configuration.
4. THE project SHALL build successfully (`:app` assembles) after the dependency is added and before any behavior changes are made.

### Requirement 2: Editor Widget Replacement

**User Story:** As a user of Pncad, I want the code pane to use the sora-editor widget, so that I get a richer editing experience in the same place as before.

#### Acceptance Criteria

1. THE `activity_main.xml` layout SHALL replace the `ScrollView > HorizontalScrollView > LinearLayout` block containing `R.id.codeEditor` and `R.id.lineNumbers` with a single `io.github.rosemoe.sora.widget.CodeEditor`.
2. THE tablet layout variant (the one exposing `R.id.paneDivider`) SHALL apply the same widget replacement in its code pane.
3. THE Editor SHALL occupy the same position and layout role in the `ViewFlipper` (phone) and split pane (tablet) as the Legacy_Editor.
4. THE separate `lineNumbers` `TextView` SHALL be removed, as the Editor renders its own line-number gutter.
5. WHEN the app launches, THE Editor SHALL be visible in the code pane and accept keyboard input.

### Requirement 3: Editor Abstraction Layer

**User Story:** As a developer, I want editor access in `MainActivity` routed through an abstraction, so that the migration's blast radius is contained and future editor changes are localized.

#### Acceptance Criteria

1. THE Editor_Adapter SHALL expose operations for: getting text, setting text, getting cursor offset, setting cursor offset, enabling/disabling editing, setting hint/placeholder text, and registering a content-change callback.
2. ALL `MainActivity` call sites that previously read or wrote the Legacy_Editor (`initViews`, `setupCodeEditor`, `generatePreview`, `renderAndExportSTL`, the `activeSession` observer, and the debounced sync watcher) SHALL access the Editor through the Editor_Adapter.
3. THE Editor_Adapter SHALL NOT change the `FileViewModel` API or the `activeSession`/`onEditorContentChanged` contract.

### Requirement 4: File Session Content Synchronization

**User Story:** As a user editing files, I want my edits tracked and persisted exactly as before, so that dirty markers, save, and multi-file tabs keep working.

#### Acceptance Criteria

1. WHEN the user edits text in the Editor, THE Content_Sync SHALL call `FileViewModel.onEditorContentChanged(text, cursor)` debounced by approximately 300 ms after the last change.
2. WHEN the active File_Session changes, THE Editor SHALL be populated with the session's `content` and its cursor set to the session's `cursorPosition`, clamped to the content length.
3. WHEN the Editor content is set programmatically from a File_Session, THE Content_Sync SHALL NOT re-fire as a user edit (equivalent to the existing `isLoadingContent` guard).
4. WHEN there is no active File_Session, THE Editor SHALL be cleared, disabled, and show the "Open a file from the File menu" hint.
5. WHEN a File_Session is active, THE Editor SHALL be enabled and show the standard code hint.

### Requirement 5: OpenSCAD Syntax Highlighting

**User Story:** As a developer editing OpenSCAD code, I want syntax highlighting at least as good as before, so that code remains readable.

#### Acceptance Criteria

1. THE OpenSCAD_Language SHALL highlight, at minimum, the categories the Legacy_Editor highlighted: keywords, built-in modules/functions, math functions, numbers, booleans (`true`/`false`/`undef`), `$`-prefixed special variables, strings, line comments (`//`), and block comments (`/* */`).
2. THE keyword, builtin, and math-function categories SHALL be driven by the existing Token_Set so the recognized tokens match the current editor.
3. THE Color_Scheme SHALL preserve the current VS Code-style palette (existing `code_background`, `code_text`, and the highlight colors defined in `SyntaxHighlighter`) within the capabilities of the chosen highlighting mechanism.
4. WHEN a large file is edited, THE Editor SHALL apply highlighting incrementally without blocking the UI thread noticeably (leveraging sora-editor's analysis pipeline).

### Requirement 6: Autocompletion Parity

**User Story:** As a developer editing OpenSCAD code, I want autocomplete for keywords, builtins, math functions, and my own declarations, so that completion works as it did before.

#### Acceptance Criteria

1. THE Editor SHALL surface completion suggestions using the existing Completion_Providers (`DocumentScanner`, `KeywordProvider`, `BuiltinProvider`, `MathProvider`) aggregated by `CompletionEngine`.
2. WHEN the user types an identifier prefix of at least the existing minimum length, THE Editor SHALL display matching suggestions.
3. WHEN the user selects a suggestion, THE Editor SHALL insert it, replacing the current prefix.
4. THE completion behavior (candidate sources, minimum prefix length, ordering) SHALL match the behavior specified by the existing editor-code-completion spec.

### Requirement 7: Preserved Editor Features and Parser Feed

**User Story:** As a user, I want line numbers, cursor behavior, and the Preview/Export actions to keep working, so that the migration is transparent to my workflow.

#### Acceptance Criteria

1. THE Editor SHALL display line numbers in its gutter.
2. WHEN the user triggers Preview or Export, THE current Editor text SHALL be read and passed to `OpenSCADParser.parse(...)` and the engine, producing the same result as before for identical input.
3. WHEN the user switches file tabs and returns, THE Editor SHALL restore the session's content and cursor position.
4. THE Editor SHALL support undo and redo of edits.

### Requirement 8: New Editor Capabilities

**User Story:** As a developer, I want the added capabilities sora-editor provides, so that the migration delivers a tangible improvement over the Legacy_Editor.

#### Acceptance Criteria

1. THE Editor SHALL support search and replace within the current document.
2. THE Editor SHALL support pinch-to-scale (text zoom).
3. THE Editor SHALL highlight matching bracket pairs.
4. These capabilities SHALL be available without regressing any requirement in this document.

### Requirement 9: Legacy Code Removal

**User Story:** As a developer, I want the now-redundant editor code removed after parity is confirmed, so that the codebase does not carry dead implementations.

#### Acceptance Criteria

1. WHEN Editor parity is verified, THE `SyntaxHighlighter`, `CompletionPopup`, and the manual line-number logic (`updateLineNumbers`) SHALL be removed.
2. THE completion domain logic (`CompletionEngine`, `CompletionProvider` implementations, `DocumentScanner`, `OpenScadTokens`) SHALL be retained and reused, not deleted.
3. THE removal SHALL NOT leave unresolved references; the `:app` module SHALL continue to build.

### Requirement 10: Verification

**User Story:** As a maintainer, I want the migration verified against concrete checks, so that I can trust it before merging.

#### Acceptance Criteria

1. THE `:app` module SHALL build successfully after the migration.
2. THE following SHALL be manually verified: launch shows the sample/loaded code; typing works; highlighting renders; autocomplete shows and inserts; line numbers display; dirty marker and save flow work; cursor restores on tab switch; Preview and Export render for valid input; undo/redo works.
3. WHERE automated tests exist for retained completion logic, THEY SHALL continue to pass.

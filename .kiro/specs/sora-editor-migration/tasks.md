# Implementation Plan: sora-editor Migration

## Overview

This plan migrates the OpenSCAD code editor from a plain `EditText` to sora-editor's `CodeEditor`. Work proceeds in a build-safe order: add the dependency (green build), introduce the `EditorAdapter` abstraction, swap the layout widget, wire the language/completion/color bridges, verify parity, then remove the legacy classes. The completion domain (`CompletionEngine`, providers, `DocumentScanner`, `OpenScadTokens`) is reused, not rewritten. Highlighting uses a custom sora `Language` driven by `OpenScadTokens` (no TextMate module in this pass).

## Tasks

- [x] 1. Add sora-editor dependency
  - Add `implementation(platform("io.github.Rosemoe.sora-editor:bom:<version>"))` and `implementation("io.github.Rosemoe.sora-editor:editor")` to `app/build.gradle.kts`, pinning `<version>` to a current stable release (0.23.x line)
  - Confirm `:app` assembles with the dependency added and no other changes
  - _Requirements: 1.1, 1.3, 1.4_

- [x] 2. Define the editor abstraction
  - [x] 2.1 Create `EditorAdapter` interface
    - Create `app/src/main/java/com/openscadviewer/editor/EditorAdapter.kt`
    - Declare `getText`, `setText`, `getCursor`, `setCursor`, `setEditable`, `setPlaceholder`, `setOnContentChanged`, and `view`
    - _Requirements: 3.1_

  - [x] 2.2 Implement `SoraEditorAdapter`
    - Create `app/src/main/java/com/openscadviewer/editor/SoraEditorAdapter.kt` wrapping a `CodeEditor`
    - Implement text get/set with a `loading` flag that suppresses the user-edit callback on programmatic `setText`
    - Implement cursor offset ↔ (line, column) conversion via the `Content` indexer; clamp offsets in `setCursor`
    - Implement `setEditable`, `setPlaceholder`, and `setOnContentChanged` via `subscribeEvent(ContentChangeEvent)`
    - Configure the widget: monospace typeface, ~13sp text size, line-number gutter on, bracket-pair highlighting, pinch-zoom/scaling
    - _Requirements: 3.1, 4.3, 7.1, 7.4, 8.2, 8.3_

- [x] 3. Implement OpenSCAD language support
  - [x] 3.1 Create `OpenScadLanguage` (sora `Language`) with incremental highlighting
    - Create `app/src/main/java/com/openscadviewer/editor/OpenScadLanguage.kt`
    - Implement an `IncrementalAnalyzeManager` that tokenizes lines and emits spans for keywords, builtins, math functions, numbers, booleans (`true`/`false`/`undef`), `$`-variables, strings, line comments, and block comments
    - Drive keyword/builtin/math classification from `OpenScadTokens`
    - Wrap tokenization so a malformed line degrades to plain text rather than crashing
    - _Requirements: 5.1, 5.2, 5.4_

  - [x] 3.2 Create `CompletionAdapter` bridging to the existing engine
    - Create `app/src/main/java/com/openscadviewer/editor/CompletionAdapter.kt`
    - Reuse the prefix-extraction logic from `CompletionTextWatcher.extractPrefix`
    - Build/hold a `CompletionEngine` from `DocumentScanner`, `KeywordProvider`, `BuiltinProvider`, `MathProvider`; publish `engine.complete(prefix)` results into sora's `CompletionPublisher`, committing by replacing the prefix
    - Drive `DocumentScanner.onTextChanged(text)` on content changes so user-defined declarations stay current
    - Wire completion into `OpenScadLanguage.requireAutoComplete(...)`
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 3.3 Define the `EditorColorScheme`
    - Create the color scheme mapping the existing palette (`code_background`, `code_text`, and the `SyntaxHighlighter` hex colors) to sora span keys per the design table
    - Apply it in `SoraEditorAdapter` configuration
    - _Requirements: 5.3_

- [x] 4. Swap the widget in the layouts
  - [x] 4.1 Replace the editor block in the phone layout
    - In `app/src/main/res/layout/activity_main.xml`, replace the `ScrollView > HorizontalScrollView > LinearLayout { lineNumbers TextView + codeEditor EditText }` with a single `io.github.rosemoe.sora.widget.CodeEditor` (id `codeEditor`) as the first `ViewFlipper` child
    - Remove the `lineNumbers` `TextView`
    - _Requirements: 2.1, 2.3, 2.4_

  - [x] 4.2 Replace the editor block in the tablet layout
    - Apply the identical widget swap in the layout variant exposing `R.id.paneDivider`
    - Verify `applyNarrowPaneFallback()` still resolves the code pane child after the swap
    - _Requirements: 2.2, 2.3_

- [x] 5. Rewire `MainActivity` through the adapter
  - [x] 5.1 Replace editor fields and `initViews`
    - Change the `codeEditor: EditText` field to `editor: EditorAdapter`; remove the `lineNumbers` field
    - In `initViews()`, construct `SoraEditorAdapter(findViewById(R.id.codeEditor))`
    - _Requirements: 3.2, 3.3_

  - [x] 5.2 Update `setupCodeEditor`
    - Construct the `CompletionEngine`/providers and attach `OpenScadLanguage` + `CompletionAdapter` via the adapter
    - Set the sample code through `editor.setText(...)`
    - Remove `SyntaxHighlighter` attach and `updateLineNumbers()` usage
    - _Requirements: 3.2, 5.1, 6.1, 7.1_

  - [x] 5.3 Update content sync and session observer
    - Replace the debounced `TextWatcher` with `editor.setOnContentChanged { text, cursor -> debounce ~300ms -> fileViewModel.onEditorContentChanged(text, cursor) }`
    - In the `activeSession` observer, use `editor.setText` (under loading guard), `editor.setCursor(clampedCursor)`, `editor.setEditable(...)`, `editor.setPlaceholder(...)`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 7.3_

  - [x] 5.4 Update parser feed sites
    - In `generatePreview()` and `renderAndExportSTL()`, read source via `editor.getText()`
    - _Requirements: 7.2_

- [x] 6. Verify parity, then remove legacy code
  - [x] 6.1 Build and run the manual verification checklist
    - Assemble `:app`; run the design's manual checklist (launch/typing, highlighting, autocomplete insert, gutter, dirty/save, tab-switch cursor restore, Preview/Export, undo/redo, search & replace, pinch-zoom)
    - _Requirements: 8.1, 8.4, 10.1, 10.2_

  - [x] 6.2 Remove redundant legacy classes
    - Delete `SyntaxHighlighter.kt`, `CompletionPopup.kt`, and `CompletionTextWatcher.kt` (prefix logic now lives in `CompletionAdapter`); remove `updateLineNumbers()`
    - Retain `CompletionEngine`, `CompletionProvider` + implementations, `DocumentScanner`, `OpenScadTokens`
    - Confirm no unresolved references and that `:app` still builds
    - _Requirements: 9.1, 9.2, 9.3, 10.1_

  - [x] 6.3 Confirm retained completion tests pass
    - Run existing unit tests for `CompletionEngine`, providers, and `DocumentScanner`
    - _Requirements: 10.3_
## Task Dependency Graph

The `waves` array groups tasks that can be executed in parallel; each wave depends on the completion of all prior waves.

```json
{
  "waves": [
    {
      "wave": 1,
      "tasks": ["1"],
      "description": "Add the sora-editor dependency; nothing compiles against sora until this lands."
    },
    {
      "wave": 2,
      "tasks": ["2.1"],
      "description": "Define the EditorAdapter interface."
    },
    {
      "wave": 3,
      "tasks": ["2.2"],
      "description": "Implement SoraEditorAdapter wrapping CodeEditor."
    },
    {
      "wave": 4,
      "tasks": ["3.1", "3.3"],
      "description": "OpenScadLanguage highlighting and the EditorColorScheme can be built in parallel."
    },
    {
      "wave": 5,
      "tasks": ["3.2"],
      "description": "CompletionAdapter wires completion into OpenScadLanguage (depends on 3.1)."
    },
    {
      "wave": 6,
      "tasks": ["4.1"],
      "description": "Swap the widget in the phone layout."
    },
    {
      "wave": 7,
      "tasks": ["4.2"],
      "description": "Swap the widget in the tablet layout."
    },
    {
      "wave": 8,
      "tasks": ["5.1"],
      "description": "Replace editor fields and initViews wiring."
    },
    {
      "wave": 9,
      "tasks": ["5.2"],
      "description": "Update setupCodeEditor (depends on 3.1, 3.2, 3.3)."
    },
    {
      "wave": 10,
      "tasks": ["5.3"],
      "description": "Update content sync and the activeSession observer."
    },
    {
      "wave": 11,
      "tasks": ["5.4"],
      "description": "Update parser feed sites (generatePreview, renderAndExportSTL)."
    },
    {
      "wave": 12,
      "tasks": ["6.1"],
      "description": "Build and run the manual verification checklist."
    },
    {
      "wave": 13,
      "tasks": ["6.2"],
      "description": "Remove redundant legacy classes (only after parity is confirmed)."
    },
    {
      "wave": 14,
      "tasks": ["6.3"],
      "description": "Run retained completion unit tests."
    }
  ]
}
```

## Notes

- Highlighting uses a custom sora `Language` driven by `OpenScadTokens`; `language-textmate` is intentionally not added in this pass and remains a documented future swap behind the unchanged `EditorAdapter` contract.
- The completion domain (`CompletionEngine`, `CompletionProvider` implementations, `DocumentScanner`, `OpenScadTokens`) is reused, not rewritten. Only the UI glue (`SyntaxHighlighter`, `CompletionPopup`, `CompletionTextWatcher`, manual line numbers) is removed.
- Cursor position stays a character offset at the `FileViewModel` boundary; line/column conversion is confined to `SoraEditorAdapter`.
- Pin the sora-editor version to a specific stable release rather than a dynamic version to keep builds reproducible.
- `CodeEditor` is a `View`, so most verification is manual on device/emulator; JVM-level unit testing is limited to the retained completion logic and, where feasible, adapter offset conversion.
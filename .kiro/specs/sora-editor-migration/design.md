# Design Document

## Overview

This design replaces the plain `EditText` OpenSCAD editor with sora-editor's `CodeEditor` widget while keeping the surrounding architecture (AppCompat Activity, XML layouts, `ViewFlipper`/split-pane, `FileViewModel`/`FileSession` state) intact. The strategy is to:

1. Add the sora-editor dependency.
2. Introduce an `EditorAdapter` abstraction so `MainActivity` never touches the concrete widget type directly.
3. Swap the widget in both the phone and tablet layouts.
4. Bridge sora's content, completion, and color APIs to the app's existing `FileViewModel` sync, `CompletionEngine`/providers, and color palette.
5. Remove the now-redundant `SyntaxHighlighter`, `CompletionPopup`, and manual line-number code once parity is confirmed, while retaining the completion domain logic.

The completion domain (`CompletionEngine`, `CompletionProvider` implementations, `DocumentScanner`, `OpenScadTokens`) is preserved and adapted into sora's completion pipeline rather than rewritten. This keeps the well-tested completion behavior and satisfies Requirement 6 and Requirement 9.2.

### Highlighting mechanism decision

Two options were considered (Requirement 5):

- **TextMate grammar** (`language-textmate` + a `.tmLanguage` bundle): richer, theme-driven, but requires sourcing/adapting an OpenSCAD grammar and bundling assets + a theme, and adds `tm4e`/`snakeyaml` weight.
- **Custom `Language` + incremental analyzer** reusing `OpenScadTokens`: fully in-code, no assets, directly reuses the existing Token_Set and keeps the palette in `EditorColorScheme`, and integrates naturally with the existing `CompletionEngine`.

**Decision: implement a custom sora `Language`** driven by `OpenScadTokens`. It is the lowest-friction path that reuses existing tokens and palette, avoids asset/grammar maintenance, and keeps highlighting and completion in one place. The `language-textmate` dependency is left as an optional future enhancement (Requirement 1.2 remains satisfied by making the module opt-in, not required for the custom path). This means Requirement 1.2's `language-textmate` is NOT added in the initial implementation; the requirement's "WHERE TextMate-based highlighting is used" guard makes it conditional, and we are not using it.

## Architecture

```
                 MainActivity (AppCompatActivity)
                        |
                        | holds
                        v
                +-----------------+        registers content-change
                |  EditorAdapter  |<-------- callback -> debounced ->
                | (interface)     |          FileViewModel.onEditorContentChanged
                +-----------------+
                        ^
                        | implements
                        |
              +----------------------+
              | SoraEditorAdapter    |  wraps io.github.rosemoe.sora.widget.CodeEditor
              +----------------------+
                        |
        +---------------+----------------+
        |                                |
        v                                v
  OpenScadLanguage                 EditorColorScheme
  (sora Language)                  (VS Code-style palette)
   |         |
   |         +--- completion --> CompletionAdapter --> CompletionEngine
   |                                                     (existing providers)
   +--- highlight --> incremental analyzer driven by OpenScadTokens
```

State flow is unchanged from the app's perspective:

- **In**: `FileViewModel.activeSession` (LiveData) → observer → `EditorAdapter.setText()` / `setCursor()` (guarded by loading flag).
- **Out**: user edit → sora content-change event → `EditorAdapter` callback → debounced (~300 ms) → `FileViewModel.onEditorContentChanged(text, cursor)`.
- **Parser feed**: Preview/Export → `EditorAdapter.getText()` → `OpenSCADParser.parse(...)` → engine.

## Components and Interfaces

### 1. `EditorAdapter` (new interface)

Isolates `MainActivity` from the concrete widget (Requirement 3).

```kotlin
interface EditorAdapter {
    fun getText(): String
    fun setText(text: String)          // programmatic; must not fire user-edit callback
    fun getCursor(): Int               // character offset
    fun setCursor(offset: Int)         // clamped by caller
    fun setEditable(enabled: Boolean)
    fun setPlaceholder(hint: String)
    /** Registers the single content-change listener. (text, cursor) on user edits only. */
    fun setOnContentChanged(listener: (text: String, cursor: Int) -> Unit)
    /** Underlying View for adding to the layout / findViewById-style access. */
    val view: View
}
```

Rationale: the current `MainActivity` touches the editor as `getText`, `setText`, `getCursor/selectionStart`, `setSelection`, `isEnabled`, `hint`, and one debounced `TextWatcher`. Those map one-to-one to the methods above, so call sites change mechanically.

### 2. `SoraEditorAdapter` (new)

Wraps a `CodeEditor` instance.

- `getText()` → `editor.text.toString()`.
- `setText(text)` → set a `loading` flag, `editor.setText(text)`, clear flag. The loading flag suppresses the user-edit callback (Requirement 4.3), mirroring the existing `isLoadingContent` guard.
- `getCursor()` → convert `editor.cursor` (line/column) to a character offset via `editor.text.getCharIndex(line, column)`.
- `setCursor(offset)` → convert offset to (line, column) via `Content` indexer and `editor.setSelection(line, column)`.
- `setEditable(enabled)` → `editor.editable = enabled`.
- `setPlaceholder(hint)` → `editor.setPlaceholderText(hint)`.
- `setOnContentChanged(listener)` → subscribe via `editor.subscribeEvent(ContentChangeEvent::class.java) { ... }`; when not in `loading` state, invoke `listener(getText(), getCursor())`.

Configuration performed on construction:
- attach `OpenScadLanguage` via `editor.setEditorLanguage(...)`,
- apply the `EditorColorScheme` (Requirement 5.3),
- enable line numbers (default on; Requirement 7.1),
- typeface monospace, text size to match current 13sp,
- enable bracket-pair highlighting, scaling/pinch-zoom (Requirement 8.2/8.3). Undo/redo (Requirement 7.4) and search & replace (Requirement 8.1) are built into `CodeEditor` (`undo()`/`redo()`, `Searcher`).

### 3. `OpenScadLanguage` (new — sora `Language`)

Implements sora's `Language` interface.

- **Highlighting**: an `AnalyzeManager`/`IncrementalAnalyzeManager` (Requirement 5.4) that tokenizes lines and emits spans for the categories in Requirement 5.1. Token classification reuses `OpenScadTokens.KEYWORDS`, `BUILTINS`, `MATH_FUNCTIONS` (Requirement 5.2) plus regex-equivalent rules for numbers, booleans (`true`/`false`/`undef`), `$`-variables, strings, and line/block comments — the same categories the current `SyntaxHighlighter` covers.
- **Completion**: `requireAutoComplete(content, position, publisher, extraArgs)` extracts the prefix at the cursor and delegates to `CompletionAdapter` (below), publishing results into sora's `CompletionPublisher`.
- **Indentation/other**: minimal `newlineHandlers`/`getIndentAdvance` as needed; not required for parity.

### 4. `CompletionAdapter` (new — thin bridge)

Bridges sora completion to the existing engine (Requirement 6).

- Holds the same `CompletionEngine` built from `DocumentScanner`, `KeywordProvider`, `BuiltinProvider`, `MathProvider`.
- On each completion request: compute the prefix (reuse the existing `WORD_CHAR_PATTERN` logic from `CompletionTextWatcher.extractPrefix`), call `engine.complete(prefix)`, and for each `CompletionItem` add a `CompletionItem`/`SimpleCompletionItem` to sora's `publisher` whose commit replaces the prefix with the item text.
- `DocumentScanner.onTextChanged(text)` is still driven on content changes (debounced 500 ms inside the scanner) so user-defined declarations stay current. This is triggered from the language's analyzer pass or the adapter's content-change hook.

Note: `CompletionEngine.MIN_PREFIX_LENGTH` (2) and provider ordering are unchanged, satisfying Requirement 6.4 and the editor-code-completion spec.

### 5. `EditorColorScheme` mapping (Requirement 5.3)

Map the existing palette to sora color-scheme keys:

| Category | Current color | sora scheme key |
|---|---|---|
| Background | `@color/code_background` | `WHOLE_BACKGROUND` / `LINE_NUMBER_BACKGROUND` |
| Default text | `@color/code_text` | `TEXT_NORMAL` |
| Keyword | `0xFF569CD6` | keyword span color |
| Builtin | `0xFF4EC9B0` | function/type span color |
| Math/function | `0xFFDCDCAA` | function span color |
| Number | `0xFFB5CEA8` | literal span color |
| String | `0xFFCE9178` | string span color |
| Comment | `0xFF6A9955` | comment span color |

Span colors are assigned via custom span type IDs registered in the color scheme.

### 6. Layout changes (Requirement 2)

Phone layout `activity_main.xml`: replace

```
ScrollView > HorizontalScrollView > LinearLayout { TextView(lineNumbers) + EditText(codeEditor) }
```

with

```xml
<io.github.rosemoe.sora.widget.CodeEditor
    android:id="@+id/codeEditor"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

as the first child of the `ViewFlipper`. The `lineNumbers` `TextView` is deleted (Requirement 2.4).

Tablet variant (layout containing `R.id.paneDivider`): apply the identical widget swap in the code pane. `MainActivity.applyNarrowPaneFallback()` continues to work since it manipulates the code pane's `layoutParams`, not the editor internals — verify the pane child lookup still resolves after the swap.

### 7. `MainActivity` wiring changes (Requirement 3, 4, 7)

- Field: `private lateinit var codeEditor: EditText` → `private lateinit var editor: EditorAdapter` (constructed from the `CodeEditor` found via `findViewById`). Remove the `lineNumbers` field.
- `initViews()`: `editor = SoraEditorAdapter(findViewById(R.id.codeEditor))`.
- `setupCodeEditor()`: construct `CompletionEngine` + providers as today, wire them into `OpenScadLanguage`/`CompletionAdapter`, set sample code via `editor.setText(...)`. Remove `SyntaxHighlighter` attach and `updateLineNumbers()`.
- `setupFileManagement()` debounced watcher: replace the `TextWatcher` with `editor.setOnContentChanged { text, cursor -> ... debounce 300ms ... fileViewModel.onEditorContentChanged(text, cursor) }`.
- `activeSession` observer: `editor.setText(session.content)` under loading guard, `editor.setCursor(clampedCursor)`, `editor.setEditable(true/false)`, `editor.setPlaceholder(...)`.
- `generatePreview()` / `renderAndExportSTL()`: `val code = editor.getText()`.

### 8. Dependency (Requirement 1)

`app/build.gradle.kts`:

```kotlin
implementation(platform("io.github.Rosemoe.sora-editor:bom:<version>"))
implementation("io.github.Rosemoe.sora-editor:editor")
```

`<version>` pinned to a current stable release (0.23.x line at time of writing). `minSdk 26` exceeds the library's minSdk (23), and JVM 17 is compatible. `language-textmate` is intentionally omitted per the highlighting decision above.

## Data Models

No new persistent data models. Existing `FileSession { content: String, cursorPosition: Int }` is unchanged; the migration only changes how content/cursor are read from and written to the widget. Cursor remains a character offset at the `FileViewModel` boundary; conversion to sora's (line, column) happens inside `SoraEditorAdapter`.

## Error Handling

- **Cursor offset out of range**: `setCursor` clamps to `[0, textLength]` before converting to (line, column); the `activeSession` observer already clamps with `minOf(cursorPosition, content.length)`.
- **Content-change re-entrancy**: the `loading` flag in `SoraEditorAdapter` prevents programmatic `setText` from firing the user-edit callback (Requirement 4.3), replacing the `isLoadingContent` role.
- **Completion on empty/short prefix**: delegated to `CompletionEngine`, which returns empty below `MIN_PREFIX_LENGTH`; the language publishes nothing.
- **Analyzer exceptions**: the language's analyze pass wraps tokenization so a malformed line degrades to plain text rather than crashing the editor.

## Testing Strategy

- **Retained unit tests**: existing tests for `CompletionEngine`, providers, and `DocumentScanner` continue to run unchanged (Requirement 10.3), since that logic is reused as-is.
- **Adapter logic**: where feasible, unit-test `SoraEditorAdapter` cursor offset ↔ (line, column) conversion and the loading-guard suppression using the `Content` API (JVM-level, no instrumentation) — best effort given `CodeEditor` is a `View`.
- **Manual verification checklist** (Requirement 10.2), run on device/emulator after build:
  1. Launch shows sample code; caret and keyboard work.
  2. Typing updates text; highlighting renders for keywords/builtins/math/strings/comments/numbers.
  3. Autocomplete popup appears at ≥2 chars and inserts, replacing the prefix.
  4. Line-number gutter is present and tracks lines.
  5. Editing marks the file dirty (`*`), Save clears it.
  6. Switch file tabs and return: content and cursor restored.
  7. Preview and Export render for valid input identical to pre-migration.
  8. Undo/redo, search & replace, and pinch-zoom work (Requirements 7.4, 8.1, 8.2).
- **Build gate**: `:app` assembles before behavior changes (Requirement 1.4) and after removal of legacy classes (Requirement 9.3, 10.1).

## Migration / Rollout Notes

- Order of operations minimizes breakage: add dependency and build (green) → introduce `EditorAdapter` over the existing `EditText` first if desired, or go straight to swap → implement language + completion bridge → verify parity → remove legacy classes.
- Legacy removal (Requirement 9): delete `SyntaxHighlighter.kt`, `CompletionPopup.kt`, `CompletionTextWatcher.kt` (its prefix logic moves into the completion bridge), and `updateLineNumbers()`. Retain `CompletionEngine`, `CompletionProvider` + implementations, `DocumentScanner`, `OpenScadTokens` (Requirement 9.2).
- If a future need for full TextMate theming arises, add `language-textmate` and swap `OpenScadLanguage` for a `TextMateLanguage` without touching the `EditorAdapter` contract.

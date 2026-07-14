# Implementation Plan: Editor Code Completion

## Overview

This plan implements a code completion system for the OpenSCAD editor. The architecture follows a provider-based pattern: shared token lists (`OpenScadTokens`), multiple `CompletionProvider` implementations (keywords, builtins, math, user-defined), a `CompletionEngine` aggregator, a `CompletionPopup` UI, and a `CompletionTextWatcher` that wires everything together. The `DocumentScanner` uses coroutines with debouncing to extract user-defined declarations without blocking the UI.

## Tasks

- [x] 1. Create shared token object and provider interface
  - [x] 1.1 Create OpenScadTokens shared object
    - Create `app/src/main/java/com/openscadviewer/editor/OpenScadTokens.kt`
    - Define `KEYWORDS`, `BUILTINS`, and `MATH_FUNCTIONS` lists matching the design specification
    - _Requirements: 1.2, 2.2, 3.2_

  - [x] 1.2 Create CompletionProvider interface and CompletionCategory enum
    - Create `app/src/main/java/com/openscadviewer/editor/CompletionProvider.kt`
    - Define `CompletionProvider` interface with `category` property and `complete(prefix: String): List<String>` method
    - Define `CompletionCategory` enum with `USER_DEFINED(0)`, `KEYWORD(1)`, `BUILTIN(2)`, `MATH(3)` priority values
    - _Requirements: 7.4_

  - [x] 1.3 Refactor SyntaxHighlighter to use OpenScadTokens
    - Update `SyntaxHighlighter.kt` companion object to reference `OpenScadTokens.KEYWORDS`, `OpenScadTokens.BUILTINS`, and `OpenScadTokens.MATH_FUNCTIONS` instead of local lists
    - Verify syntax highlighting still works with shared token source
    - _Requirements: 1.2, 2.2, 3.2_

- [x] 2. Implement static completion providers
  - [x] 2.1 Implement KeywordProvider
    - Create `app/src/main/java/com/openscadviewer/editor/KeywordProvider.kt`
    - Implement `CompletionProvider` with `CompletionCategory.KEYWORD`
    - Pre-sort keywords alphabetically, filter by case-insensitive prefix match
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 2.2 Implement BuiltinProvider
    - Create `app/src/main/java/com/openscadviewer/editor/BuiltinProvider.kt`
    - Implement `CompletionProvider` with `CompletionCategory.BUILTIN`
    - Pre-sort builtins alphabetically, filter by case-insensitive prefix match
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 2.3 Implement MathProvider
    - Create `app/src/main/java/com/openscadviewer/editor/MathProvider.kt`
    - Implement `CompletionProvider` with `CompletionCategory.MATH`
    - Pre-sort math functions alphabetically, filter by case-insensitive prefix match
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 2.4 Write property test for provider filtering (Property 1)
    - Create `app/src/test/kotlin/com/openscadviewer/editor/CompletionProviderPropertyTest.kt`
    - **Property 1: Provider filtering returns exactly matching candidates in sorted order**
    - Generate random prefixes (2-10 chars from `[a-zA-Z_][a-zA-Z0-9_]*`); verify against reference filter implementation
    - Test all three static providers (KeywordProvider, BuiltinProvider, MathProvider)
    - **Validates: Requirements 1.1, 1.3, 1.4, 2.1, 2.3, 2.4, 3.1, 3.3, 7.1, 7.3**

- [x] 3. Implement DocumentScanner
  - [x] 3.1 Implement DocumentScanner class
    - Create `app/src/main/java/com/openscadviewer/editor/DocumentScanner.kt`
    - Implement `CompletionProvider` with `CompletionCategory.USER_DEFINED`
    - Implement `onTextChanged(text: String)` with 500ms debounced coroutine scan
    - Implement `scan(text: String)` that strips comments then extracts `module <name>(` and `function <name>(` patterns
    - Use `@Volatile` for `cachedDeclarations` to ensure cross-thread visibility
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 8.2, 8.5_

  - [x] 3.2 Write property test for declaration extraction (Property 3)
    - Create `app/src/test/kotlin/com/openscadviewer/editor/DocumentScannerPropertyTest.kt`
    - **Property 3: Document scanner extracts all non-commented declarations**
    - Generate random OpenSCAD-like text with `module X(` and `function Y(` patterns inside and outside comments
    - Verify scanner returns sorted unique names only from non-commented regions
    - **Validates: Requirements 4.1, 4.2, 4.5, 4.6**

  - [x] 3.3 Write property test for scanner deduplication (Property 4)
    - Add to `DocumentScannerPropertyTest.kt`
    - **Property 4: Document scanner deduplication**
    - Generate text with repeated declaration names
    - Verify each name appears exactly once in output
    - **Validates: Requirements 4.3**

- [x] 4. Implement CompletionEngine
  - [x] 4.1 Implement CompletionEngine and CompletionItem
    - Create `app/src/main/java/com/openscadviewer/editor/CompletionEngine.kt`
    - Define `CompletionItem` data class with `text: String` and `category: CompletionCategory`
    - Implement `complete(prefix: String): List<CompletionItem>` with min-prefix check, priority ordering, and case-insensitive deduplication
    - Define `MIN_PREFIX_LENGTH = 2` constant
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 8.1_

  - [x] 4.2 Write property test for short prefix rejection (Property 2)
    - Create `app/src/test/kotlin/com/openscadviewer/editor/CompletionEnginePropertyTest.kt`
    - **Property 2: Short prefix produces empty results**
    - Generate random strings of length 0-1
    - Verify `CompletionEngine.complete(prefix)` always returns empty list
    - **Validates: Requirements 3.4, 7.2**

  - [x] 4.3 Write property test for priority ordering and deduplication (Property 5)
    - Add to `CompletionEnginePropertyTest.kt`
    - **Property 5: Engine priority ordering and deduplication**
    - Create multiple providers with overlapping candidate names
    - Verify results are ordered by category priority and duplicates appear only once from highest-priority provider
    - **Validates: Requirements 7.4, 7.5**

- [x] 5. Checkpoint - Verify core logic
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement CompletionPopup UI
  - [x] 6.1 Implement CompletionPopup class
    - Create `app/src/main/java/com/openscadviewer/editor/CompletionPopup.kt`
    - Use `PopupWindow` anchored to EditText with cursor-relative positioning
    - Build content view with `ScrollView` > `LinearLayout` > `TextView` items
    - Use monospace font at 13sp, dark background (0xFF1E1E1E), light text (0xFFD4D4D4)
    - Implement `show()`, `dismiss()`, `update()`, `isShowing()` methods
    - Set `MAX_VISIBLE_ITEMS = 5` for scroll threshold
    - Fire `onItemSelected` callback on tap
    - _Requirements: 5.1, 5.2, 5.3, 5.6, 5.7_

- [x] 7. Implement CompletionTextWatcher and insertion logic
  - [x] 7.1 Implement CompletionTextWatcher class
    - Create `app/src/main/java/com/openscadviewer/editor/CompletionTextWatcher.kt`
    - Implement `TextWatcher` that extracts prefix from cursor position using `[a-zA-Z_][a-zA-Z0-9_]*$` regex
    - Trigger `documentScanner.onTextChanged()` on each edit (scanner handles debouncing)
    - Query `engine.complete(prefix)` and call `popup.update()` or `popup.dismiss()`
    - Implement `insertCompletion(item: CompletionItem)` that replaces prefix atomically via `Editable.replace()`
    - Use `isInserting` flag to prevent re-triggering during insertion
    - _Requirements: 5.3, 5.4, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 8.3_

  - [x] 7.2 Write property test for prefix extraction (Property 6)
    - Create `app/src/test/kotlin/com/openscadviewer/editor/PrefixExtractionPropertyTest.kt`
    - **Property 6: Prefix extraction correctness**
    - Generate random text strings with random cursor positions
    - Verify `extractPrefix(text, cursorPos)` returns the longest trailing identifier match or empty string
    - **Validates: Requirements 7.1, 7.2**

  - [x] 7.3 Write property test for insertion correctness (Property 7)
    - Create `app/src/test/kotlin/com/openscadviewer/editor/CompletionInsertionPropertyTest.kt`
    - **Property 7: Insertion replaces exactly the prefix**
    - Generate random documents with cursor positions and completion items
    - Verify resulting text equals `text[0..start) + item.text + text[cursorPos..end)` and cursor is at `start + item.text.length`
    - **Validates: Requirements 6.1, 6.2**

- [x] 8. Integrate into MainActivity
  - [x] 8.1 Wire completion system in setupCodeEditor()
    - In `MainActivity.setupCodeEditor()`, instantiate `DocumentScanner` with `lifecycleScope`
    - Instantiate `KeywordProvider`, `BuiltinProvider`, `MathProvider`
    - Create `CompletionEngine` with all four providers
    - Create `CompletionPopup` with selection callback wired to `CompletionTextWatcher.insertCompletion()`
    - Create `CompletionTextWatcher` and attach to `codeEditor`
    - Ensure completion TextWatcher coexists with existing line-number TextWatcher
    - _Requirements: 5.1, 5.4, 5.5, 6.1, 8.1, 8.3, 8.4, 8.5_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses `net.jqwik:jqwik:1.8.4` for property-based testing
- All Kotlin source goes in `app/src/main/java/com/openscadviewer/editor/`
- All test source goes in `app/src/test/kotlin/com/openscadviewer/editor/`
- `kotlinx-coroutines-test` is available for testing debounced DocumentScanner behavior

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["2.4", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "4.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "6.1"] },
    { "id": 5, "tasks": ["7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3", "8.1"] }
  ]
}
```

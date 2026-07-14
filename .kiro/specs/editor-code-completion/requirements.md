# Requirements Document

## Introduction

This feature adds code completion to the OpenSCAD code editor in the Pncad Android app. The editor (a plain EditText) will display autocomplete suggestions for OpenSCAD keywords, built-in modules/functions, math functions, and user-defined module/function declarations as the user types. Suggestions appear in a dropdown popup anchored to the cursor position, and selecting a suggestion inserts the completed text at the cursor.

## Glossary

- **Editor**: The EditText widget (id: codeEditor) used for editing OpenSCAD source code in the app.
- **Completion_Popup**: A dropdown list displayed near the cursor showing matching completion suggestions.
- **Suggestion**: A single entry in the Completion_Popup representing a completable token.
- **Prefix**: The sequence of word characters (matching `[a-zA-Z_][a-zA-Z0-9_]*`) immediately before the cursor used to filter suggestions.
- **Keyword_Provider**: The component that supplies OpenSCAD language keywords as completion candidates.
- **Builtin_Provider**: The component that supplies built-in OpenSCAD modules and functions as completion candidates.
- **Math_Provider**: The component that supplies OpenSCAD math functions as completion candidates.
- **Document_Scanner**: The component that parses the current editor text to extract user-defined module and function declarations.
- **Completion_Engine**: The component that aggregates candidates from all providers and filters them against the current Prefix.

## Requirements

### Requirement 1: Keyword Completion

**User Story:** As a developer editing OpenSCAD code, I want to see autocomplete suggestions for language keywords, so that I can type them quickly without memorizing exact spelling.

#### Acceptance Criteria

1. WHEN the user types a Prefix of at least 2 characters that matches the beginning of an OpenSCAD keyword using case-insensitive comparison, THE Completion_Engine SHALL include all matching keywords in the suggestion list.
2. THE Keyword_Provider SHALL supply the following keywords as candidates: module, function, if, else, for, let, each, assert, echo, include, use.
3. WHEN the Prefix matches no keywords, THE Keyword_Provider SHALL return an empty list.
4. THE Keyword_Provider SHALL return matching keywords in alphabetical order.

### Requirement 2: Built-in Module and Function Completion

**User Story:** As a developer editing OpenSCAD code, I want to see autocomplete suggestions for built-in modules and functions, so that I can discover and use available primitives without consulting documentation.

#### Acceptance Criteria

1. WHEN the user types a Prefix of at least 2 characters that matches the beginning of a built-in name using case-insensitive prefix comparison, THE Completion_Engine SHALL include all matching built-in names in the suggestion list.
2. THE Builtin_Provider SHALL supply at minimum the following built-in names as candidates: cube, sphere, cylinder, polyhedron, circle, square, polygon, text, translate, rotate, scale, mirror, multmatrix, color, offset, hull, minkowski, union, difference, intersection, linear_extrude, rotate_extrude, import, surface, projection, render, children.
3. WHEN the Prefix matches no built-in names, THE Builtin_Provider SHALL return an empty list.
4. THE Builtin_Provider SHALL return each built-in name at most once in the suggestion list, ordered alphabetically.

### Requirement 3: Math Function Completion

**User Story:** As a developer editing OpenSCAD code, I want to see autocomplete suggestions for math functions, so that I can use the correct function names without errors.

#### Acceptance Criteria

1. WHEN the user types a Prefix of at least 2 characters that matches the beginning of a math function name using case-insensitive comparison, THE Completion_Engine SHALL include all math function names whose beginning matches the Prefix in the suggestion list, ordered alphabetically.
2. THE Math_Provider SHALL supply at minimum the following function names as candidates: abs, sign, sin, cos, tan, asin, acos, atan, atan2, floor, ceil, round, sqrt, pow, exp, log, ln, min, max, len, norm, cross, concat, lookup, str.
3. WHEN the Prefix matches no math function names, THE Math_Provider SHALL return an empty list.
4. IF the user has typed fewer than 2 characters as the Prefix, THEN THE Completion_Engine SHALL NOT display math function suggestions.

### Requirement 4: User-Defined Declaration Completion

**User Story:** As a developer editing OpenSCAD code, I want to see my own module and function names offered as completions, so that I can quickly reference declarations I have already written.

#### Acceptance Criteria

1. WHEN the current editor text contains a module declaration matching the pattern `module <name>(` where `<name>` is an identifier matching `[a-zA-Z_][a-zA-Z0-9_]*`, THE Document_Scanner SHALL extract that name as a completion candidate.
2. WHEN the current editor text contains a function declaration matching the pattern `function <name>(` where `<name>` is an identifier matching `[a-zA-Z_][a-zA-Z0-9_]*`, THE Document_Scanner SHALL extract that name as a completion candidate.
3. WHEN the user types a Prefix of at least 2 characters that matches the beginning of a user-defined name using case-insensitive comparison, THE Completion_Engine SHALL include matching user-defined names in the suggestion list with each unique name appearing at most once regardless of how many times it is declared.
4. WHEN the editor text changes, THE Document_Scanner SHALL re-scan the text to update the set of user-defined candidates within 500ms of the last keystroke (debounced).
5. THE Document_Scanner SHALL extract declarations from the entire editor text, including declarations that appear after the current cursor position.
6. IF a module or function declaration pattern appears inside a line comment (starting with `//`) or inside a block comment (between `/*` and `*/`), THEN THE Document_Scanner SHALL exclude that declaration from the set of completion candidates.

### Requirement 5: Suggestion Popup Display

**User Story:** As a developer editing OpenSCAD code, I want the completion suggestions to appear in a readable popup near my cursor, so that I can see options without losing context.

#### Acceptance Criteria

1. WHEN the Completion_Engine produces one or more matching suggestions, THE Completion_Popup SHALL display the list of suggestions positioned below the current line of text if vertical space is available, or above the current line otherwise.
2. THE Completion_Popup SHALL display a maximum of 5 suggestions at a time, with the list being scrollable when more suggestions are available.
3. WHEN the Completion_Engine produces zero suggestions, THE Completion_Popup SHALL remain hidden. IF the Completion_Popup was previously visible and the updated suggestion list becomes empty, THEN THE Completion_Popup SHALL dismiss.
4. WHEN the Completion_Popup is visible and the user continues typing, THE Completion_Engine SHALL update the suggestion list to reflect the new Prefix within 100ms.
5. WHEN the Completion_Popup is visible and the user moves the cursor to a different line or position via tap (not via typing), THE Completion_Popup SHALL dismiss.
6. WHEN the Completion_Popup is visible and the user presses the back button or taps outside the popup, THE Completion_Popup SHALL dismiss.
7. THE Completion_Popup SHALL use a monospace font at 13sp consistent with the Editor font and a dark background consistent with the app's dark theme.

### Requirement 6: Suggestion Selection and Insertion

**User Story:** As a developer editing OpenSCAD code, I want to select a suggestion to insert the completed word, so that I can avoid typing the full identifier manually.

#### Acceptance Criteria

1. WHEN the user taps a Suggestion in the Completion_Popup, THE Editor SHALL replace the current Prefix with the full text of the selected Suggestion.
2. WHEN the replacement is inserted, THE Editor SHALL place the cursor immediately after the inserted text.
3. WHEN the replacement is inserted, THE Completion_Popup SHALL dismiss.
4. THE Editor SHALL replace the Prefix atomically such that the replacement and the Prefix removal are treated as a single undo operation.
5. WHEN the replacement is inserted, THE SyntaxHighlighter SHALL re-apply syntax highlighting to reflect the updated text.

### Requirement 7: Prefix Filtering

**User Story:** As a developer editing OpenSCAD code, I want the completion to match case-insensitively, so that I can find suggestions regardless of the case I type.

#### Acceptance Criteria

1. THE Completion_Engine SHALL perform case-insensitive prefix matching when filtering candidates against the Prefix (e.g., "Cu" matches "cube", "CY" matches "cylinder").
2. WHEN the Prefix is fewer than 2 characters, THE Completion_Engine SHALL return an empty suggestion list and THE Completion_Popup SHALL not be displayed.
3. THE Completion_Engine SHALL order suggestions alphabetically (case-insensitive) within each provider category.
4. THE Completion_Engine SHALL present suggestions grouped in the following priority order: user-defined names first, keywords second, built-in names third, math functions fourth.
5. IF the same name appears in multiple provider categories, THE Completion_Engine SHALL include it only in the highest-priority category to avoid duplicates.

### Requirement 8: Performance and Non-Interference

**User Story:** As a developer editing OpenSCAD code, I want autocomplete to be responsive and not interfere with typing, so that the editor remains smooth to use.

#### Acceptance Criteria

1. THE Completion_Engine SHALL produce filtered suggestions within 50ms of a Prefix change for documents up to 10,000 lines.
2. THE Document_Scanner SHALL perform re-scanning on a background thread (using Kotlin coroutines with Dispatchers.Default) to avoid blocking the UI thread.
3. WHILE the user is typing, THE Editor SHALL remain responsive to input regardless of whether the Completion_Engine is computing suggestions. Input latency SHALL NOT increase by more than 16ms due to completion processing.
4. THE Completion_Popup SHALL appear within 100ms after the Completion_Engine produces a non-empty suggestion list.
5. WHEN the Document_Scanner is re-scanning after a text change, THE Completion_Engine SHALL use the last-known set of user-defined candidates until the new scan completes, ensuring suggestions are always available without delay.

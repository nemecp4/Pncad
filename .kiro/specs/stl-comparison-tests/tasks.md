# Implementation Plan: STL Comparison Tests

## Overview

Enhance the existing `StlComparator` to compare both triangle count and file size metrics, add a `StlComparisonAssert` utility for failing tests when deviations exceed 15%, and integrate the assertion into `BenchmarkTest`. Property-based tests using jqwik validate the correctness properties defined in the design.

## Tasks

- [x] 1. Enhance StlComparator with file size comparison
  - [x] 1.1 Refactor ComparisonResult data class to include file size metrics
    - Replace the existing `ComparisonResult` in `StlComparator.kt` with the enhanced version
    - Add fields: `generatedFileSize`, `expectedFileSize`, `trianglesMatch`, `fileSizeMatch`
    - Add computed properties: `triangleRatio`, `trianglePercentDiff`, `fileSizeRatio`, `fileSizePercentDiff`
    - Add `withinTolerance(tolerance: Double)` method with zero-expected guards
    - Remove old `match`, `ratio`, `percentDiff` fields
    - _Requirements: 2.1, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 1.2 Update StlComparator.compare() to include file size and tolerance
    - Add `tolerance` parameter with default 0.15
    - Read file size via `File.length()` for both generated and reference files
    - Compute `trianglesMatch` and `fileSizeMatch` based on tolerance threshold
    - Return `null` if either file doesn't exist or is unreadable
    - _Requirements: 2.1, 2.2, 2.3, 6.1, 6.2_

  - [x] 1.3 Write property tests for ComparisonResult tolerance logic
    - **Property 4: Percent difference formula correctness**
    - **Property 5: Zero-expected values never cause false failure**
    - **Property 6: Tolerance boundary is inclusive**
    - **Validates: Requirements 2.4, 2.5, 3.3, 3.4, 3.5, 6.3, 6.4**

  - [x] 1.4 Write property test for triangle count reading
    - **Property 3: Triangle count reading correctness**
    - Generate synthetic binary STL byte arrays (≥ 84 bytes) with known triangle counts at bytes 80–83
    - Write to temp file and verify `readTriangleCount` returns the expected value
    - **Validates: Requirements 1.1**

- [x] 2. Implement StlComparisonAssert utility
  - [x] 2.1 Create StlComparisonAssert object
    - Create `StlComparisonAssert.kt` in the benchmark test package
    - Implement `assertAllWithinTolerance(comparisons, tolerance)` that collects all failures and throws a single `AssertionError`
    - Implement `assertWithinTolerance(result, tolerance)` for single-result assertion
    - Error messages must include test name, actual vs expected values, percent difference, and threshold
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 2.2 Write property tests for StlComparisonAssert
    - **Property 1: All within tolerance implies test passes**
    - **Property 2: Any exceeding tolerance implies test fails**
    - **Property 7: Assertion error message contains all failing test names**
    - **Validates: Requirements 3.1, 3.2, 4.1, 4.2, 4.3**

- [x] 3. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Integrate into BenchmarkTest and update dependent code
  - [x] 4.1 Update BenchmarkRunner.compareWithReferences() for new ComparisonResult
    - Adjust any code that consumes the old `ComparisonResult` fields (`match`, `ratio`, `percentDiff`)
    - Ensure `compareWithReferences` passes through the tolerance or uses default
    - _Requirements: 5.1, 5.3_

  - [x] 4.2 Update TimingSummaryFormatter for new ComparisonResult fields
    - Update references from old `match`/`percentDiff` to new `trianglesMatch`/`fileSizeMatch`/`trianglePercentDiff`/`fileSizePercentDiff`
    - Add file size comparison info to the summary output
    - _Requirements: 2.4, 2.5_

  - [x] 4.3 Add StlComparisonAssert call to BenchmarkTest
    - After `compareWithReferences()`, call `StlComparisonAssert.assertAllWithinTolerance(comparisons, 0.15)`
    - Only assert when `comparisons.isNotEmpty()` to handle the empty-list case gracefully
    - _Requirements: 5.2, 5.4_

- [x] 5. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik
- Unit tests validate specific examples and edge cases
- The existing `readTriangleCount` implementation is preserved; only `compare()` and `ComparisonResult` change
- All code is Kotlin targeting JVM 17, consistent with existing benchmark module

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.2"] },
    { "id": 3, "tasks": ["4.1", "4.2"] },
    { "id": 4, "tasks": ["4.3"] }
  ]
}
```

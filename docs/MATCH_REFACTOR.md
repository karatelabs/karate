# Match System Refactoring - COMPLETED

This document describes the completed refactoring of the Match system in `io.karatelabs.match`.

## Status: Complete

All phases have been implemented and tested.

## Summary of Changes

### Phase 1: Large Collection Support ✓

**Goal:** Avoid loading entire collections into memory when size exceeds threshold.

**Implementation:**
- Added `LargeValueStore` interface for abstracted collection access
- Added `DiskBackedList` implementation using JSON Lines format for disk storage
- Modified `Value` class with 10MB memory threshold and lazy store creation
- Size estimation uses random sampling (5 items) to avoid O(n) overhead
- Updated `Operation` to use iterator patterns for collection traversal

**Files:**
- NEW: `LargeValueStore.java`
- NEW: `DiskBackedList.java`
- MODIFIED: `Value.java`
- MODIFIED: `Operation.java`
- NEW: `DiskBackedListTest.java`

### Phase 2: Structured Failure Results ✓

**Goal:** Return structured failure data alongside string messages.

**Implementation:**
- Added `Failure` record with path, reason, types, values, and depth
- Extended `Result` class with `List<Failure> failures` field
- Added `getResult()` method to `Operation` that builds structured failures
- Backward compatible - `Result.message` still works

**Files:**
- MODIFIED: `Result.java`
- MODIFIED: `Operation.java`

### Phase 3: Collect All Failures ✓

**Goal:** Report all mismatches instead of stopping at first failure.

**Implementation:**
- Removed early-return patterns in 6 locations
- Added collection of failed indices/keys before returning
- Error messages now show all failures (e.g., "failed at indices [0, 2, 5]")
- Preserved early-exit for `CONTAINS_ANY` (correct behavior)

**Performance Note:** No overhead on happy path. Success requires checking ALL elements anyway (O(n)). The only difference is in failure cases, where we continue iterating - but failures are exceptional and benefit from more diagnostic data.

**Files:**
- MODIFIED: `Operation.java`

### Phase 4: WITHIN Assertion ✓

**Goal:** Add reverse-CONTAINS assertion type.

**Implementation:**
- Added `WITHIN` and `NOT_WITHIN` to `Match.Type` enum
- Added `actualWithinExpected()` method supporting LIST, MAP, STRING, XML
- Added `within()` and `notWithin()` convenience methods to `Value`
- Added DSL support: `match subset within superset`

**DSL Syntax:**
```gherkin
* match [1, 2] within [1, 2, 3, 4, 5]
* match { name: 'foo' } within { name: 'foo', age: 30 }
* match subset !within other
```

**Files:**
- MODIFIED: `Match.java`
- MODIFIED: `Operation.java`
- MODIFIED: `Value.java`
- MODIFIED: `js.flex` (lexer)
- MODIFIED: `MatchExpression.java`
- MODIFIED: `GherkinParser.java`

## Test Coverage

- `MatchTest.java` - Core match functionality and multi-failure messages
- `DiskBackedListTest.java` - Disk-backed storage and low-threshold testing
- `GherkinParserTest.java` - DSL parsing for `within` and `!within`
- `StepMatchTest.java` - End-to-end DSL integration tests

## Commits

1. `beb99bc` - refactor: enhance Match system with multi-failure collection and WITHIN assertion
2. `45afd3d` - improve large collection handling and add tests
3. `8535be0` - feat: add WITHIN assertion support to Karate DSL

Feature: callonce feature isolation A

  # This feature uses callonce to call a helper that increments a counter.
  # When run in parallel with callonce-feature-b.feature, each feature should
  # execute the helper independently (feature-scoped isolation, not suite-scoped).

  Background:
    * callonce read('callonce-counter-helper.feature')

  Scenario: Feature A scenario 1
    * print 'Feature A, Scenario 1, executionCount:', executionCount
    * assert executionCount > 0

  Scenario: Feature A scenario 2
    # Should see the SAME executionCount as scenario 1 (within same feature)
    * print 'Feature A, Scenario 2, executionCount:', executionCount
    * assert executionCount > 0

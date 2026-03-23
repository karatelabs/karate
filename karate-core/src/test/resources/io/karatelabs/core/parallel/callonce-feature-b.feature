Feature: callonce feature isolation B

  # This feature uses callonce to call a helper that increments a counter.
  # When run in parallel with callonce-feature-a.feature, each feature should
  # execute the helper independently (feature-scoped isolation, not suite-scoped).

  Background:
    * callonce read('callonce-counter-helper.feature')

  Scenario: Feature B scenario 1
    * print 'Feature B, Scenario 1, executionCount:', executionCount
    * assert executionCount > 0

  Scenario: Feature B scenario 2
    # Should see the SAME executionCount as scenario 1 (within same feature)
    * print 'Feature B, Scenario 2, executionCount:', executionCount
    * assert executionCount > 0

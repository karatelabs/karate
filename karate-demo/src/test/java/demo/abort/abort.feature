Feature: abort should skip (but not fail) a test

Scenario: you can conditionally exit a test
    but please use sparingly

  * configure abortedStepsShouldPass = true
  * print 'before'
  * if (true) karate.abort()
  * print 'after'
    
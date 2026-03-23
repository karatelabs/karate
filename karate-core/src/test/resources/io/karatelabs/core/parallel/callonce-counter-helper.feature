Feature: callonce counter helper

  # This feature is called via callonce by multiple features.
  # It increments a global counter to track how many times it's actually executed.
  # If callOnce is feature-scoped: each feature should call this once (counter = N features)
  # If callOnce were suite-scoped (bug): only one feature would call this (counter = 1)

  Scenario:
    * def Counter = Java.type('io.karatelabs.core.parallel.CallOnceCounter')
    * def executionCount = Counter.incrementAndGet()

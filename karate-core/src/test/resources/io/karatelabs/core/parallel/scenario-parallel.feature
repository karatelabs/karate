Feature: Scenario-level parallelism test

  # All scenarios in this feature should be able to run in parallel
  # This test verifies that scenarios within a single feature run concurrently

  Scenario: parallel scenario 1
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('scenario-parallel', 'scenario1')
    # Simulate work to allow overlap with other scenarios
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('scenario-parallel', 'scenario1')

  Scenario: parallel scenario 2
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('scenario-parallel', 'scenario2')
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('scenario-parallel', 'scenario2')

  Scenario: parallel scenario 3
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('scenario-parallel', 'scenario3')
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('scenario-parallel', 'scenario3')

  Scenario: parallel scenario 4
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('scenario-parallel', 'scenario4')
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('scenario-parallel', 'scenario4')

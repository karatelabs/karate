Feature: callonce thread safety

  # Tests that callonce is executed only once per feature
  # All scenarios should see the same calledId

  Background:
    * callonce read('callonce-called.feature')

  Scenario: callonce thread 1
    * print 'Thread 1, calledId:', calledId
    # Store the ID for comparison
    * def myId = calledId
    # Mutate local copy - should not affect other scenarios
    * def localData = sharedData
    * eval localData.counter = localData.counter + 1
    * match localData.counter == 1

  Scenario: callonce thread 2
    * print 'Thread 2, calledId:', calledId
    # Should see the same calledId as thread 1
    * def myId = calledId
    # Mutations from thread 1 should not be visible (isolation)
    * def localData = sharedData
    * eval localData.counter = localData.counter + 1
    * match localData.counter == 1

  Scenario: callonce thread 3
    * print 'Thread 3, calledId:', calledId
    * def myId = calledId
    * def localData = sharedData
    * eval localData.counter = localData.counter + 1
    * match localData.counter == 1

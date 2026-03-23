Feature: callSingle thread safety

  # Tests that callSingle returns the same instance ID across all scenarios
  # Even though scenarios run in parallel, they should all see the same instanceId

  Background:
    # Get the singleton ID from config (which comes from callSingle)
    * def expectedId = singleData.instanceId

  Scenario: thread 1 - verify callSingle consistency
    * print 'Thread 1, expectedId:', expectedId
    * def currentId = getSingleId()
    * match currentId == expectedId

    # Verify we can access nested data
    * match singleData.data.nested.value == 'original'

  Scenario: thread 2 - verify callSingle consistency
    * print 'Thread 2, expectedId:', expectedId
    * def currentId = getSingleId()
    * match currentId == expectedId

  Scenario: thread 3 - verify callSingle consistency
    * print 'Thread 3, expectedId:', expectedId
    * def currentId = getSingleId()
    * match currentId == expectedId

  Scenario: thread 4 - verify callSingle consistency
    * print 'Thread 4, expectedId:', expectedId
    * def currentId = getSingleId()
    * match currentId == expectedId

  Scenario: thread 5 - verify callSingle consistency
    * print 'Thread 5, expectedId:', expectedId
    * def currentId = getSingleId()
    * match currentId == expectedId

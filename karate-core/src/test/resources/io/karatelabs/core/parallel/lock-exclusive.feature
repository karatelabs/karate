Feature: Exclusive lock test

  # Test @lock=* - should run exclusively, no other scenarios running concurrently

  @lock=*
  Scenario: exclusive scenario 1
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('exclusive', 'exclusive1')
    # Verify we're the only one running
    * match concurrent == 1
    # Simulate some work
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('exclusive', 'exclusive1')

  Scenario: non-locked scenario
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('none', 'nonlocked')
    * def sleep = function() { java.lang.Thread.sleep(30) }
    * eval sleep()
    * eval LockTestTracker.exit('none', 'nonlocked')

  @lock=*
  Scenario: exclusive scenario 2
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('exclusive', 'exclusive2')
    # Verify we're the only one running
    * match concurrent == 1
    * def sleep = function() { java.lang.Thread.sleep(100) }
    * eval sleep()
    * eval LockTestTracker.exit('exclusive', 'exclusive2')

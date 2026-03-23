Feature: Lock test feature A

  # Test @lock=shared - should run sequentially with other @lock=shared scenarios

  @lock=shared
  Scenario: locked scenario A1
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('shared', 'A1')
    # Simulate some work
    * def sleep = function() { java.lang.Thread.sleep(50) }
    * eval sleep()
    * eval LockTestTracker.exit('shared', 'A1')

  @lock=shared
  Scenario: locked scenario A2
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('shared', 'A2')
    * def sleep = function() { java.lang.Thread.sleep(50) }
    * eval sleep()
    * eval LockTestTracker.exit('shared', 'A2')

Feature: Lock test feature B

  # Test @lock=shared - should run sequentially with other @lock=shared scenarios

  @lock=shared
  Scenario: locked scenario B1
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('shared', 'B1')
    # Simulate some work
    * def sleep = function() { java.lang.Thread.sleep(50) }
    * eval sleep()
    * eval LockTestTracker.exit('shared', 'B1')

  @lock=shared
  Scenario: locked scenario B2
    * def LockTestTracker = Java.type('io.karatelabs.core.parallel.LockTestTracker')
    * def concurrent = LockTestTracker.enter('shared', 'B2')
    * def sleep = function() { java.lang.Thread.sleep(50) }
    * eval sleep()
    * eval LockTestTracker.exit('shared', 'B2')

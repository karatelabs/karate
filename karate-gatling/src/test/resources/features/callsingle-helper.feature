Feature: callSingle helper — increments a static counter each time it runs

  Scenario: bump
    * def n = Java.type('io.karatelabs.gatling.CallSingleCounter').incrementAndGet()

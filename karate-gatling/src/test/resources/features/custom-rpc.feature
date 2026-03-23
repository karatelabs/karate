Feature: Java Interop Test

  Tests custom performance event capture via PerfContext.
  The TestUtils.myRpc() method captures a "custom-rpc" event
  that should appear in Gatling reports.

  Scenario: Custom RPC with perf event
    * def Utils = Java.type('io.karatelabs.gatling.TestUtils')
    * def result = Utils.myRpc({ data: 'test' }, karate)
    * match result.success == true
    * assert result.duration >= 0

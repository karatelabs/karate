Feature: Fails before any HTTP request

  This scenario fails on an assertion before issuing any HTTP request. Such a
  failure has no HTTP perf event to ride on, so the Gatling integration must emit
  a synthetic KO — otherwise the failure would vanish from the load report.

  Scenario: assertion fails with no http call
    * def actual = 'hello'
    * match actual == 'goodbye'

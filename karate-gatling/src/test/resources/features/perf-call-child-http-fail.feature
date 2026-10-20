Feature: Caller with no perf event of its own, calling a feature that makes a request and fails

  Scenario: call a failing feature that made a request
    * call read('classpath:features/perf-child-http-fail.feature')

Feature: Caller with no perf event of its own, calling a feature that captures one and fails

  Scenario: call a failing feature that captured a custom event
    * call read('classpath:features/perf-child-event-fail.feature')

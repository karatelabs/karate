Feature: Root caller with no perf event of its own, calling a middle feature that calls a failing leaf

  Scenario: call a middle feature whose callee captured a custom event and failed
    * call read('classpath:features/perf-call-chain-middle.feature')

Feature: Middle feature with no perf event of its own, calling a leaf that captures one and fails

  Scenario: call a failing leaf that captured a custom event
    * call read('classpath:features/perf-call-chain-leaf.feature')

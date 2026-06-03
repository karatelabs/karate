Feature: Caller with failure

  Scenario: Scenario 1
    * call read('util.feature@print') {text: "Scenario 1"}
    * karate.fail('Error scenario 1')

  Scenario: Scenario 2
    * call read('util.feature@print') {text: "Scenario 2"}

@ignore
Feature:

Scenario: called scenario
  * utils.sayHello()
  * karate.call('dummy.feature')
  * utils.sayHello()
  * karate.call(true, 'dummy.feature')
  * utils.sayHello()
  * call read('dummy.feature')
  * utils.sayHello()
  * utils.reuseExistingFunction()
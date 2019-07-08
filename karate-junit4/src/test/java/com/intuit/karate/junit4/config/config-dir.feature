@ignore
Feature: config dir over-ride

Scenario: check if ./karate-config-custom.js was invoked
    * match defaultoverride == 'worked'
    * match envoverride == 'done'
    * match baseconfig == 'overridden'
    * match greeter.sayHello('John') == 'Hello John!'
    * match utils.hello() == 'hello'
    * match utils.world() == 'world'
    
    # call with param set
    * def res = call child { param: 'foo' }
    * match res.value == 'child-foo'

    # call without param set, ensure above child scope is re-set
    * def res = call child {  }
    * match res.value == 'child-default'

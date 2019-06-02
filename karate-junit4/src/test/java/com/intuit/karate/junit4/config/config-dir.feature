@ignore
Feature: config dir over-ride

Scenario: check if ./karate-config-custom.js was invoked
    * match defaultoverride == 'worked'
    * match envoverride == 'done'
    * match baseconfig == 'overridden'
    * match greeter.sayHello('John') == 'Hello John!'

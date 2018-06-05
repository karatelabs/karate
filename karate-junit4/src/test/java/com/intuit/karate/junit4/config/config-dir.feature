@ignore
Feature: config dir over-ride

Scenario: check if ./karate-config-custom.js was invoked
    * match diroverride == 'worked'
    * match envoverride == 'done'

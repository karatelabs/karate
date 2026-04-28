@ignore
Feature: Two UI scenarios in a called feature - both should share the inherited driver

Scenario: first called UI scenario
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'

Scenario: second called UI scenario
* driver serverUrl + '/wait.html'
* match driver.title == 'Wait Test'

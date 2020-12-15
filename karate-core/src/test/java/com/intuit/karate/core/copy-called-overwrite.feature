@ignore
Feature: called file that may or may not over-write variable in caller

Scenario:
* def someString = 'after'
* def someJson = { value: 'after' }
* def fromCalled = { hello: 'world' }
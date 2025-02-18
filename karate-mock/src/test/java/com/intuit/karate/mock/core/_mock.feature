Feature: Testing Generic mock

Background:
    * def predicate1Impl = Java.type('com.intuit.karate.mock.core.PredicateImpl1')
    * def predicate2Impl = Java.type('com.intuit.karate.mock.core.PredicateImpl2')
    * def predicate1 = new predicate1Impl()
    * def predicate2 = new predicate2Impl()


Scenario: predicate(predicate1)
    * print ' predicate should not be invoked '

Scenario: predicate(predicate2)
    * print ' predicate is invoked '
    * def karateMockResponseMessage = 'TestPayload2'
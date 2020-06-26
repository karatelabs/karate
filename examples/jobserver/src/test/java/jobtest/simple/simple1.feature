Feature: simple 1

Background:
* configure responseDelay = 1000

Scenario: 1-one
* print '1-one'
* def karateTest = java.lang.System.getenv('KARATE_TEST')
* print '*** KARATE_TEST: ', karateTest

Scenario: 1-two
* print '1-two'

Scenario: 1-three
* print '1-three'

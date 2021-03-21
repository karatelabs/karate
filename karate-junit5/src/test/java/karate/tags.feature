Feature: tags test

@first
Scenario: first
  * print 'first'

@second
Scenario: second
  * print 'second'
  * print 'system property foo:', karate.properties['foo']
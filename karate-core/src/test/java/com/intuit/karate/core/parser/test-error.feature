@ignore
Feature: the first line

Scenario: has name
  Given def a = 1
  And def b = 2
  When def c = a + b
  Then match c == 3
  And print 'success !'

Scenario:
  Given def a = 1
  And def b = 2
  When def c = a + b
  Then match c == 2
  And print 'success !'

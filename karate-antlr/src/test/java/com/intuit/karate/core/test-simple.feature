@foo
Feature: the first line
    and the second

@bar
Scenario:
  Given def a = 1
  And def b = 2
  When def c = a + b
  Then match c == 3
  And print 'success !'

Feature:

Background:
* def a = 1

Scenario:
    * assert a == 1
    * def a = 2
    * def b = 3

Scenario:
    * assert a == 1
    * assert typeof b == 'undefined'

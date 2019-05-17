Feature: make sure a callonce (with shared scope) in the background 
    does not leak variables created in the scenarios

Background:
* callonce read('called-noop.feature')

Scenario: first
  * assert typeof email == 'undefined'
  * def email = "admin@admin.com"

Scenario: second
  * assert typeof email == 'undefined'

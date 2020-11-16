@ignore
Feature: fail2pass1
Background:
Given def a = 1

Scenario: failure1
Then assert a != 1
And assert a == 2

Scenario: failure2
Then assert a != 2
And assert a == 3

Scenario: pass1
Then assert a != 4
And assert a != 5

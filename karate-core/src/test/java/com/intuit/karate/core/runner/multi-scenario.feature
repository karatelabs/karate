Feature: multi scenario

Background:
Given def a = 1

Scenario: first 
Then assert a == 1

Scenario: second 
Then assert a != 2


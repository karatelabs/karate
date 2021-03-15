Feature: simple feature file

# some comment

Background:
Given def a = 1

Scenario: test
Then assert a == 1
# another comment
When def b = 
"""
{ foo: 'bar' }
"""
Then match b == { foo: 'bar'}


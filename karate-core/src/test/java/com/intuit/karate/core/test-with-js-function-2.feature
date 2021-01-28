Feature: Test Feature with function from global config

Background:
  * def data = [ { name: 'value' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
  * print 'test'

Examples:
  | data |
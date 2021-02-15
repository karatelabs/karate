@trigger-by-tag
Feature: function from global config


Background:
 * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
 * print 'b'
 * print baseHelper.doSomething(data)

 Examples:
  | data |

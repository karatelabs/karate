Feature: function from global config

Background:
 * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
* print 'name:', name

  Examples:
| data |

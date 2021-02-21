Feature:

Background:
  * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
  * match functionFromKarateBase() == 'fromKarateBase'

Examples:
  | data |

Feature:

@setup
Scenario:
 * def data = [{ name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' }]

Scenario Outline:
* print 'name:', name

  Examples:
| karate.setup().data |

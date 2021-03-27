@trigger-by-tag
Feature:

Background:
 # background http builder should work even for a dynamic scenario outline
 * url serverUrl
 * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
 * match functionFromKarateBase() == 'fromKarateBase'
 * path 'fromfeature'
 * method get
 * status 200
 * match response == { message: 'from feature' }

 Examples:
  | data |

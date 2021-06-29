@trigger-by-tag
Feature:

Background:
 # background http builder should work even for a dynamic scenario outline
 * url serverUrl
 * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]
 # java object that comes from a callSingle in the config
 * def HelloBg = HelloConfigSingle
 * callonce read('call-once-from-feature.feature')

Scenario Outline:
 * call read('called.feature')
 * match functionFromKarateBase() == 'fromKarateBase'
 * path 'fromfeature'
 * method get
 * status 200
 * match response == { message: 'from feature' }

 * match HelloBg.sayHello('world') == 'hello world'
 * match HelloOnce.sayHello('world') == 'hello world'
 * match sayHello('world') == 'hello world'

 Examples:
  | data |

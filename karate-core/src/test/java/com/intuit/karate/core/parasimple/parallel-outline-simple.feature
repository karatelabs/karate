Feature:

Background:
* call read('headers.feature')

@setup
Scenario:
 * def data = [{ name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' }] 

Scenario Outline:
 * url 'http://localhost:' + karate.properties['server.port']
 * path 'fromfeature'
 * method get
 * status 200
 * match response == { message: 'from feature' }

 Examples:
  | karate.setup().data |


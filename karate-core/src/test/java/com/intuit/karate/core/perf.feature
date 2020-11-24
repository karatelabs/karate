Feature:

Background:
  * url 'http://localhost:' + karate.properties['karate.server.port']

Scenario:
* path 'hello'
* param foo = foo
* method get
* status 200
* match response == { foo: ['bar'] }

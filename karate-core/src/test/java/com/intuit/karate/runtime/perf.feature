Feature:

Scenario:
* url 'http://localhost:' + karate.properties['karate.server.port']
* path 'hello'
* param foo = foo
* method get
* match response == { foo: ['bar'] }

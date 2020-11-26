Feature:

Background:
  * url 'http://localhost:' + karate.properties['karate.server.port']

@name=pass
Scenario:
* path 'hello'
* param foo = bar
* method get
* status 200
* match response == { foo: [#(bar)] }

@name=failStatus
Scenario:
* path 'hello'
* param foo = bar
* method get
# The following line will fail
* status 500

@name=failResponse
Scenario:
* url 'http://localhost:' + karate.properties['karate.server.port']
* path 'hello'
* param foo = bar
* method get
* status 200
# The following line will fail
* match response == {}


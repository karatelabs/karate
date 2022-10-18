Feature:

Background:
* url serverUrl
* callonce read('call-once-from-feature.feature')

Scenario: 1
* match HelloOnce.sayHello('world') == 'hello world'

Scenario: 2
* match HelloOnce.sayHello('world') == 'hello world'

Scenario: 3
* match HelloOnce.sayHello('world') == 'hello world'

Scenario: N
* call sayHelloOnce 'three'

* path 'three'
* method get
* status 200
* match response == { three: '#string' }

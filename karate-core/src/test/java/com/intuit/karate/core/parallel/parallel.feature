Feature:

Background:
# background http builder should work even if a callonce follows
* url serverUrl
* match message == 'from config'
* callonce read('call-once-from-feature.feature')
* match message == 'from common'
* match message2 == 'fromCallSingleFromConfig2'

Scenario: one
* call sayHelloOnce 'one'
* path 'one'
* method get
* status 200
* match response == { one: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }

* match HelloConfigSingle.sayHello('world') == 'hello world'
* match HelloOnce.sayHello('world') == 'hello world'
* match sayHello('world') == 'hello world'
* match sayHelloOnce('world') == 'hello world'

Scenario: two
* call sayHelloOnce 'two'
* path 'two'
* method get
* status 200
* match response == { two: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }

* match HelloConfigSingle.sayHello('world') == 'hello world'
* match HelloOnce.sayHello('world') == 'hello world'
* match sayHello('world') == 'hello world'
* match sayHelloOnce('world') == 'hello world'

Scenario: three
* call sayHelloOnce 'three'
* path 'three'
* method get
* status 200
* match response == { three: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }

* match HelloConfigSingle.sayHello('world') == 'hello world'
* match HelloOnce.sayHello('world') == 'hello world'
* match sayHello('world') == 'hello world'
* match sayHelloOnce('world') == 'hello world'

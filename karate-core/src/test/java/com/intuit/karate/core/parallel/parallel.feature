Feature:

Background:
# background http builder should work even if a callonce follows
* url serverUrl
* match message == 'from config'
* callonce read('common.feature')
* match message == 'from common'
* match message2 == 'fromCallSingleFromConfig2'

Scenario: one
* path 'one'
* method get
* status 200
* match response == { one: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }

Scenario: two
* path 'two'
* method get
* status 200
* match response == { two: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }

Scenario: three
* path 'three'
* method get
* status 200
* match response == { three: '#string' }
* def result = karate.callSingle('call-single-from-feature.feature')
* match result.response == { message: 'from feature' }
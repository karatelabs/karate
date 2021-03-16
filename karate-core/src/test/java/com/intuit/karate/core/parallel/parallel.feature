Feature:

Background:
* match message == 'hello world'
* callonce read('common.feature')
* match fromCommon == 'hello common'
* url serverUrl

Scenario: one
* path 'one'
* method get
* status 200
* match response == { one: '#string' }

Scenario: two
* path 'two'
* method get
* status 200
* match response == { two: '#string' }

Scenario: three
* path 'three'
* method get
* status 200
* match response == { three: '#string' }
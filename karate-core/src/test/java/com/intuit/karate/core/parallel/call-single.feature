Feature:

Scenario:
* url serverUrl
* path 'config'
* method get
* status 200
* match response == { message: 'hello world' }

Feature:

Scenario:
* url serverUrl
* path 'fromconfig'
* method get
* status 200
* match response == { message: 'from config' }

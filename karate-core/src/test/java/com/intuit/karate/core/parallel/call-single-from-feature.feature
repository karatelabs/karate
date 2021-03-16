Feature:

Scenario:
* url serverUrl
* path 'fromfeature'
* method get
* status 200
* match response == { message: 'from feature' }

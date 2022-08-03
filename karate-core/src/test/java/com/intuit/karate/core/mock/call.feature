Feature:

Background:
* url mockServerUrl

  Scenario:
    * path 'call-shared'
    * method get
    * status 200
    * match response == 'hello world'

  Scenario:
    * path 'call-isolated'
    * method get
    * status 200
    * match response == 'hello world'
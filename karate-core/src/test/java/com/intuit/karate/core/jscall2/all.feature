Feature: responseStatus variable tests

  Scenario: config js test
    * url serverUrl
    * method get
    * status 200
    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200_config()

  Scenario: call test
    * url serverUrl
    * method get
    * status 200
    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200_call()
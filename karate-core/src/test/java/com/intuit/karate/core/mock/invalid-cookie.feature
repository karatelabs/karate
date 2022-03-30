Feature:

Background:
* url mockServerUrl

  Scenario:
    * path 'invalid-cookie';
    * method get
    * status 200

    # check that 'invalid' cookie is passed to the next call
    * method get
    * status 404
    * def temp = karate.prevRequest
    * def invalidCookie = temp.headers['Cookie']
    * match invalidCookie contains ["detectedTimeZoneId=FLE Standard Time"]
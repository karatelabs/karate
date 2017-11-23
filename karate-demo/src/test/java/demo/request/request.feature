Feature: test accessing the 'actual' request made

Background:
* url demoBaseUrl

Scenario: create cat
    Given path 'cats'
    And request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { id: '#number', name: 'Billie' }

    * def temp = (karate.lastRequest)
    * def requestMethod = (temp.method)
    * match requestMethod == 'POST'
    * def requestHeaders = (temp.headers)
    * match requestHeaders contains { 'Content-Type': ['application/json'] }
    * def requestUri = (temp.uri)
    * match requestUri == (demoBaseUrl + '/cats')
    # this will be of java type byte[]
    * def requestBody = (temp.body)
    # convert byte array to  string
    * def requestString = (new java.lang.String(requestBody, 'utf-8'))
    * match requestString == '{"name":"Billie"}'
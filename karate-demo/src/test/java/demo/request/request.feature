@mock-servlet-todo
Feature: test accessing the 'actual' request made

Background:
* url demoBaseUrl

Scenario: create cat
    Given path 'cats'
    And param foo = 'bar'
    And request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { id: '#number', name: 'Billie' }

    * def temp = karate.prevRequest
    * def requestMethod = temp.method
    * match requestMethod == 'POST'
    * def requestHeaders = temp.headers
    * def contentType = temp.headers['Content-Type'][0]
    * match contentType contains 'application/json'
    * match contentType contains 'charset=UTF-8'
    * def requestUri = temp.uri
    * match requestUri == demoBaseUrl + '/cats?foo=bar'
    # this will be of java type byte[]
    * def requestBody = temp.body
    # convert byte array to  string
    * def requestString = new java.lang.String(requestBody, 'utf-8')
    * match requestString == '{"name":"Billie"}'

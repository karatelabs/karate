Feature: modifying cookies

Background:
* url demoBaseUrl

Scenario: no cookies
    Given path 'search', 'cookies'
    When method get
    Then status 200
    And match response == []

Scenario: one cookie, and it is sent automatically in the next request
    Given path 'search', 'cookies'
    And cookie foo = 'bar'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'bar' }
    
    Given path 'search', 'cookies'
    And request {}
    When method post
    Then status 200
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'bar' }   
    
    * print 'cookies: ', responseCookies

    # reset cookies
    * configure cookies = null
    Given path 'search', 'cookies'    
    When method get
    Then status 200
    And match response == []

    # modify cookies
    Given path 'search', 'cookies'  
    And cookie foo = 'blah'
    And request {}
    When method post
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'blah' }      


    

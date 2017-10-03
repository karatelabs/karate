Feature: content-type header with a charset

Background:
* url demoBaseUrl

Scenario: json post    
    Given path 'search', 'headers'
    And header Content-Type = 'application/json; charset=utf-8'
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/json;')
    * assert temp.contains('charset=utf-8')

Scenario: form post
    Given path 'search', 'headers'
    And header Content-Type = 'application/x-www-form-urlencoded; charset=utf-8'
    And form field foo = 'bar'
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/x-www-form-urlencoded;')
    * assert temp.contains('charset=utf-8')

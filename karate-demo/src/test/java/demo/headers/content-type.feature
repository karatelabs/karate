Feature: exotic content-type situations

Background:
* url demoBaseUrl

Scenario: json post with charset   
    Given path 'search', 'headers'
    And header Content-Type = 'application/json; charset=utf-8'
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/json;')
    * assert temp.contains('charset=utf-8')

Scenario: form post with charset
    Given path 'search', 'headers'
    And header Content-Type = 'application/x-www-form-urlencoded; charset=utf-8'
    And form field foo = 'bar'
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/x-www-form-urlencoded;')
    * assert temp.contains('charset=utf-8')

Scenario: json post with with charset and version
    Given path 'search', 'headers'
    And header Content-Type = 'application/json; charset=utf-8; version=1.2.3'
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/json;')
    * assert temp.contains('charset=utf-8')
    * assert temp.contains('version=1.2.3')

@mock-servlet-todo
Scenario: json post with with unusual content-type and parameter
    Given path 'search', 'headers'
    And header Content-Type = 'application/vnd.app.test+json;ton-version=1'
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/vnd.app.test+json;')
    * assert temp.contains('charset=utf-8')
    * assert temp.contains('ton-version=1')

@apache @mock-servlet-todo
Scenario: empty string as content-type
    Given path 'search', 'headers'
    And header Content-Type = ''
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0]
    * assert temp == ''

Scenario: json post with header but NO charset   
    Given path 'search', 'headers'
    And configure charset = null
    And header Content-Type = 'application/json'
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/json')
    * assert !temp.contains('charset=utf-8')

Scenario: json post with default header but NO charset   
    Given path 'search', 'headers'
    And configure charset = null
    And request { foo: 'bar' }
    When method post
    Then status 200
    * def temp = response['content-type'][0].toLowerCase()
    * assert temp.contains('application/json')
    * assert !temp.contains('charset=utf-8')


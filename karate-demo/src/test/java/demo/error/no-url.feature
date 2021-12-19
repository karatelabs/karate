@mock-servlet-todo
Feature:  No URL found proper error response

  Background:
    * url demoBaseUrl
    * configure lowerCaseResponseHeaders = true

  Scenario: Invalid URL response
    Given path 'hello'
    When method get
    Then status 404
    And match header content-type contains 'application/json'
    And match response.status == 404
    And match response.path == '/hello'
    And match response.error == 'Not Found'

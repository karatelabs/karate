@mock-servlet-todo
Feature: disable redirects in order to assert against the location header

Background:
* url demoBaseUrl

Scenario: get redirects are followed by default
    Given path 'redirect'
    And param foo = 'bar'
    When method get
    Then status 200
    And match response == { foo: ['bar'] }

Scenario: get redirects can be disabled
    * configure followRedirects = false
    Given path 'redirect'
    When method get
    Then status 302
    And match header Location == demoBaseUrl + '/search'

    * def location = responseHeaders['Location'][0]

    Given url location
    And param foo = 'bar'
    When method get
    Then status 200
    And match response == { foo: ['bar'] }

Scenario: post redirects are followed by default
    Given path 'redirect'
    And param foo = 'bar'
    And request {}
    When method post
    Then status 200
    And match response == { foo: ['bar'] }

Scenario: post redirects can be disabled
    * configure followRedirects = false
    Given path 'redirect'
    And request {}
    When method post
    Then status 302
    And match header Location == demoBaseUrl + '/search'

    * def location = responseHeaders['Location'][0]

    Given url location
    And param foo = 'bar'
    When method get
    Then status 200
    And match response == { foo: ['bar'] }

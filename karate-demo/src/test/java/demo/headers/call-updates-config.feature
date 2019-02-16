@mock-servlet-todo
Feature: a called feature can 'contribute' to variables and config 
    including headers and cookies

Background:
# the shape of the next line is important. if the line starts with call (or callonce)
# the called script will update the 'shared scope' here in this file
# think of it as similar to an 'include' directive in some programming languages
# detailed documentation: https://github.com/intuit/karate#shared-scope
* callonce read('common.feature')
* url demoBaseUrl

# for an example of NOT using 'shared scope'
# refer to 'call-isolated-headers.feature' and 'common-multiple.feature'

Scenario: no extra config - they have been set automatically by 'common.feature'
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: make sure that the second scenario works as well with callonce
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: here we erase the configured headers to get a 400
    * configure headers = null
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 400

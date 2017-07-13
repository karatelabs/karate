Feature: a called feature will not clobber the parent context
    if the variable assignment syntax is used

Background:
* def setup = callonce read('common.feature')
* url demoBaseUrl
* cookie time = setup.time

Scenario: configure function

    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    # error bad request
    Then status 400

Scenario: configure json

    * configure headers = setup.headersJson
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200

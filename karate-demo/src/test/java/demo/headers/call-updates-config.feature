Feature: a called feature can 'contribute' to variables and config including headers

Background:
* callonce read('common.feature')
* url demoBaseUrl
* cookie time = time

Scenario: configure function

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: configure json

    * configure headers = headersJson
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

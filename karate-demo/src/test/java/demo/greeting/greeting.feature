Feature: greeting end-point

Background:
* url demoBaseUrl

Scenario: get default greeting

    Given path 'greeting'
    When method get
    Then status 200
    And match response == { id: '#number', content: 'Hello World!' }

Scenario: get custom greeting

    Given path 'greeting'
    And param name = 'Billie'
    When method get
    Then status 200
    And match response == { id: '#number', content: 'Hello Billie!' }

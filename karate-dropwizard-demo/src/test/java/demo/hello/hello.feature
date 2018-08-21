Feature: cats end-point

  Background:
    * url demoBaseUrl

  Scenario: create and retrieve a cat

    Given path 'hello'
    When method get
    Then status 200
    * assert response.name == 'Bahubali'


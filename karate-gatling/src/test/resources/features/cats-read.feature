Feature: Read Cat

  Background:
    * url baseUrl

  Scenario: Read cat by ID from previous feature
    # Get cat ID from previous Karate feature execution
    * def catId = __karate.catId

    Given path 'cats', catId
    When method get
    Then status 200
    And match response contains { id: '#(catId)' }

Feature: Cats CRUD Operations

  Background:
    * url baseUrl

  Scenario: Create and read a cat
    # Create a new cat
    Given path 'cats'
    And request { name: 'Fluffy', age: 3 }
    When method post
    Then status 201
    And match response contains { id: '#string', name: 'Fluffy', age: 3 }
    * def catId = response.id

    # Read the created cat
    Given path 'cats', catId
    When method get
    Then status 200
    And match response == { id: '#(catId)', name: 'Fluffy', age: 3 }

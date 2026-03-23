Feature: Create Cat with Gatling Variables

  Background:
    * url baseUrl

  Scenario: Create a cat using feeder data
    # Get name from Gatling session (set via karateSet)
    * def catName = __gatling.name
    * def catAge = karate.get('__gatling.age', 1)

    Given path 'cats'
    And request { name: '#(catName)', age: '#(catAge)' }
    When method post
    Then status 201
    And match response contains { id: '#string', name: '#(catName)' }

    # Store cat ID for next feature in chain
    * def catId = response.id

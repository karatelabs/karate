Feature: cats integration test

Background:
    * url karate.properties['mock.cats.url']

Scenario: create and get cats
    Given request { name: 'Billie' }
    When method post
    Then status 200    
    And match response == { id: '#uuid', name: 'Billie' }

    * def id1 = response.id
    Given path id1
    When method get
    Then status 200
    And match response == { id: '#(id1)', name: 'Billie' }

    When method get
    Then status 200
    And match response contains { id: '#(id1)', name: 'Billie' }

    Given request { name: 'Bob' }
    When method post
    Then status 200    
    And match response == { id: '#uuid', name: 'Bob' }

    * def id2 = response.id
    Given path id2
    When method get
    Then status 200
    And match response == { id: '#(id2)', name: 'Bob' }

    When method get
    Then status 200
    And match response contains [{ id: '#(id1)', name: 'Billie' },{ id: '#(id2)', name: 'Bob' }]

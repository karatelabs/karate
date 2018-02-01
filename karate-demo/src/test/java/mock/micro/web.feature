Feature: cats integration test

Background:
    * url karate.properties['mock.cats.url'] || 'http://localhost:8080/cats'

Scenario: create cat
    Given request { name: 'Billie' }
    When method post
    Then status 200    
    And match response == { id: '#uuid', name: 'Billie' }
    And def id = response.id

    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: 'Billie' }

    When method get
    Then status 200
    And match response == [{ id: '#(id)', name: 'Billie' }]

    Given request { name: 'Bob' }
    When method post
    Then status 200    
    And match response == { id: '#uuid', name: 'Bob' }
    And def id = response.id

    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: 'Bob' }

    When method get
    Then status 200
    And match response == [{ id: '#uuid', name: 'Billie' },{ id: '#(id)', name: 'Bob' }]
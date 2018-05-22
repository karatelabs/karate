Feature: cats integration test

Background:
    * url karate.properties['mock.cats.url']

Scenario: create cat
    Given request { name: 'Billie' }
    When method post
    Then status 200    
    And match response == { id: 1, name: 'Billie' }

    Given path 1
    When method get
    Then status 200
    And match response == { id: 1, name: 'Billie' }

    When method get
    Then status 200
    And match response == [{ id: 1, name: 'Billie' }]

    Given request { name: 'Bob' }
    When method post
    Then status 200    
    And match response == { id: 2, name: 'Bob' }

    Given path 2
    When method get
    Then status 200
    And match response == { id: 2, name: 'Bob' }

    When method get
    Then status 200
    And match response == [{ id: 1, name: 'Billie' },{ id: 2, name: 'Bob' }]

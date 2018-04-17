Feature: cats crud

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: create, get and update cat
    Given request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { id: '#uuid', name: 'Billie' }

    * def id = response.id
    Given path id
    When method get
    Then status 200
    # intentional assertion failure
    And match response == { id: '#(id)', name: 'Billi' }

    Given path id
    When request { id: '#(id)', name: 'Bob' }
    When method put
    Then status 200
    And match response == { id: '#(id)', name: 'Bob' }

    When method get
    Then status 200
    # And match response contains { id: '#(id)', name: 'Bob' }


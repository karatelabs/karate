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
    # this step may randomly fail because another thread is doing deletes
    Then status 200
    And match response == { id: '#(id)', name: 'Billie' }

    Given path id
    When request { id: '#(id)', name: 'Bob' }
    When method put
    Then status 200
    # intentional assertion failure
    And match response == { id: '#(id)', name: 'Bobb' }

    # since we failed above, these lines will not be executed
    When method get
    Then status 200
    And match response contains { id: '#(id)', name: 'Bob' }

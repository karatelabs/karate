Feature: cats crud

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: create, get and update cat
    # example of using the gatling session / feeder data
    # note how this can still work as a normal test, without gatling
    * def name = karate.get('__gatling.catName', 'Billie')
    Given request { name: '#(name)' }
    When method post
    Then status 200
    And match response == { id: '#uuid', name: '#(name)' }
    * def id = response.id

    Given path id
    When method get
    # this step may randomly fail because another thread is doing deletes
    Then status 200
    # intentional assertion failure
    And match response == { id: '#(id)', name: 'Billi' }

    # since we failed above, these lines will not be executed
    Given path id
    When request { id: '#(id)', name: 'Bob' }
    When method put
    Then status 200
    And match response == { id: '#(id)', name: 'Bob' }

    When method get
    Then status 200
    And match response contains { id: '#(id)', name: 'Bob' }

Feature: cats integration test

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: create and get cats
    Given request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { id: '#uuid', name: 'Billie' }

    * def id = response.id
    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: 'Billie' }

    When method get
    Then status 200
    # intentional assertion failure
    And match response contains { id: '#(id)', name: 'Billi' }

    Given path id
    When request { id: '#(id)', name: 'Bob' }
    When method put
    Then status 200
    And match response == { id: '#(id)', name: 'Bob' }

    When method get
    Then status 200
    And match response contains { id: '#(id)', name: 'Bob' }

    Given path id
    When method delete
    Then status 200
    And match response == ''

    When method get
    Then status 200
    And match response !contains { id: '#(id)', name: '#string' }

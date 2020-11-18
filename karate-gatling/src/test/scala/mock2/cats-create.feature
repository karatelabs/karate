Feature:

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: create and get cat
    * def name = __gatling.name
    * print 'Creating cat with name ' + name
    Given request { name: #(name) }
    When method post
    Then status 200
    And match response == { id: '#uuid', name: #(name) }
    * def id = response.id

    * print 'Getting cat with id ' + id
    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: #(name) }

    # Save id
    And def extra = { id: #(id) }
    And def updated = karate.merge(__gatling, extra)
    And karate.set('__gatling', updated)

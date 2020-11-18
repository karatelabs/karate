Feature:

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: delete cat and verify
    * def id = __gatling.id
    * print 'Deleting cat with id ' + id
    Given path id
    When method delete
    Then status 200

    * print 'Getting cat with id ' + id
    Given path id
    When method get
    Then status 404

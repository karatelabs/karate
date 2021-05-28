Feature: cats crud

  Background:
    * url baseUrl

  @name=create
  Scenario: create

    Given request { name: '#(name)' }
    When method post
    Then status 200
    And match response == { id: '#uuid', name: '#(name)' }
    * def id = response.id

  @name=read
  Scenario: read
    # note how 'id' and 'expectedName' are passed in via the gatling session
    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: '#(expectedName)' }


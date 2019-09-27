Feature: integration test for the mock
    for help, see: https://github.com/intuit/karate/wiki/ZIP-Release

Background:
    * def port = karate.env == 'mock' ? karate.start('cats-mock.feature').port : 8080
    * url 'http://localhost:' + port + '/cats'

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
    And match response contains [{ id: '#(id)', name: 'Billie' }]

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
    And match response contains [{ id: '#uuid', name: 'Billie' },{ id: '#(id)', name: 'Bob' }]

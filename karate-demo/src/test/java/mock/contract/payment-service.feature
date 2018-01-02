Feature: payment service

Background:
* url paymentServiceUrl + '/payments'

Scenario: create, get, list and delete payments
    Given request { amount: 5.67, description: 'test one' }
    When method post
    Then status 200
    And match response == { id: '#number', amount: 5.67, description: 'test one' }
    And def id = response.id

    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', amount: 5.67, description: 'test one' }

    When method get
    Then status 200
    And match response contains { id: '#(id)', amount: 5.67, description: 'test one' }

    Given path id
    When method delete
    Then status 200

    When method get
    Then status 200
    And match response !contains { id: '#(id)', amount: 5.67, description: 'test one' }
    

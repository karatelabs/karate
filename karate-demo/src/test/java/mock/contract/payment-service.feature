Feature: payment service

Background:
* def QueueConsumer = Java.type('mock.contract.QueueConsumer')
* def queue = new QueueConsumer(queueName)
* def handler = function(msg){ karate.signal(msg) }
* queue.listen(handler)
* url paymentServiceUrl + '/payments'

Scenario: create, get, update, list and delete payments
    Given request { amount: 5.67, description: 'test one' }
    When method post
    Then status 200
    And match response == { id: '#number', amount: 5.67, description: 'test one' }
    And def id = response.id
    * json shipment = karate.listen(5000)
    * print '### received:', shipment
    * match shipment == { paymentId: '#(id)', status: 'shipped' }

    Given path id
    When method get
    Then status 200
    And match response == { id: '#(id)', amount: 5.67, description: 'test one' }

    Given path id
    And request { id: '#(id)', amount: 5.67, description: 'test two' }
    When method put
    Then status 200
    And match response == { id: '#(id)', amount: 5.67, description: 'test two' }

    When method get
    Then status 200
    And match response contains { id: '#(id)', amount: 5.67, description: 'test two' }

    Given path id
    When method delete
    Then status 200

    When method get
    Then status 200
    And match response !contains { id: '#(id)', amount: '#number', description: '#string' }
    
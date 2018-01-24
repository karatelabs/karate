Feature: payment service mock

Background:
* def nextId = call read('increment.js')
* def payments = {}
* def QueueUtils = Java.type('mock.contract.QueueUtils')
* configure cors = true

Scenario: pathMatches('/payments') && methodIs('post')
    * def payment = request
    * def id = nextId()
    * set payment.id = id
    * eval payments[id + ''] = payment
    * def response = payment
    * string json  = { paymentId: '#(id)', status: 'shipped' }
    * eval QueueUtils.send(queueName, json, 25)    

Scenario: pathMatches('/payments')
    * def response = $payments.*

Scenario: pathMatches('/payments/{id}') && methodIs('put')
    * eval payments[pathParams.id] = request
    * def response = request

Scenario: pathMatches('/payments/{id}') && methodIs('delete')
    * eval karate.remove('payments', '$.' + pathParams.id)
    * def response = ''

Scenario: pathMatches('/payments/{id}')
    * def response = payments[pathParams.id]

Scenario: pathMatches('/')
    * def responseHeaders = { 'Content-Type': 'text/html' }
    * def response = read('payments.html')

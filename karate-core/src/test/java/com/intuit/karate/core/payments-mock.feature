Feature:

Background:
* def counter = 0
* def payments = {}

Scenario: pathMatches('/payments') && methodIs('post')
* def payment = request
* def counter = counter + 1
* def id = '' + counter
* payment.id = id
* payments[id] = payment
* def response = payment 

Scenario: pathMatches('/payments')
* def response = $payments.*

Scenario: pathMatches('/payments/{id}') && methodIs('put')
* payments[pathParams.id] = request
* def response = request

Scenario: pathMatches('/payments/{id}') && methodIs('delete')
* delete payments[pathParams.id]

Scenario: pathMatches('/payments/{id}')
* def response = payments[pathParams.id]
* def responseStatus = response ? 200 : 404

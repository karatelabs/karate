@ignore
Feature: simple example of a karate test

Scenario: create and retrieve a cat

Given url 'http://localhost:' + wiremockPort + '/v1/cats'
And request { name: 'Billie' }
When method post
Then status 201
And match response == { id: '#ignore', name: 'Billie' }
# And assert responseTime < 1000

Given path response.id
When method get
Then status 200

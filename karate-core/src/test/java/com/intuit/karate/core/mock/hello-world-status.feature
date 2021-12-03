Feature: simple example of a karate test

Background:
# to test that expressions also work for the method keyword
* def postMethod = 'post'
* def getMethod = 'get'
* def status201 = 201
* def status200 = 200

Scenario: create and retrieve a cat

Given url mockServerUrl + 'cats'
And request { name: 'Billie' }
When method postMethod
Then status status201
And match response == { id: '#ignore', name: 'Billie' }
# And assert responseTime < 1000

Given path response.id
When method getMethod
Then status status200

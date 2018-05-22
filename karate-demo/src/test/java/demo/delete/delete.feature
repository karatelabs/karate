Feature: test delete

Background:
* url demoBaseUrl

Given path 'cats'
And request { name: 'Billie' }
When method post
Then status 200
* def cat = response

Scenario: normal delete without a body

Given path 'cats', cat.id
When method delete
Then status 200

Scenario: delete with a request body

Given path 'cats'
And request cat
When method delete
Then status 200

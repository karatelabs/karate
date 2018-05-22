Feature: cats end-point

Background:
* url demoBaseUrl

Scenario: create and retrieve a cat

# create a new cat
Given path 'cats'
And request { name: 'Billie' }
When method post
Then status 200
And match response == { id: '#number', name: 'Billie' }

* def id = response.id

# get by id
Given path 'cats', id
When method get
Then status 200
And match response == { id: '#(id)', name: 'Billie' }

# update
Given path 'cats', id
And request { id: '#(id)', name: 'Billie Edit' }
When method put
Then status 200
And match response == { id: '#(id)', name: 'Billie Edit' }

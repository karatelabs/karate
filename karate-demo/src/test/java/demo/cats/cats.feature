Feature: cats end-point

Background:
* url demoBaseUrl
* configure logPrettyRequest = true
* configure logPrettyResponse = true

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

# get all cats
Given path 'cats'
When method get
Then status 200
And match response contains { id: '#(id)', name: 'Billie' }

# get cat but ask for xml
Given path 'cats', id
And header Accept = 'application/xml'
When method get
Then status 200
And match response == <cat><id>#(id)</id><name>Billie</name></cat>

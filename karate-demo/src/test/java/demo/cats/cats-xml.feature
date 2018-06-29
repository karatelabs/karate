Feature: cats end-point and xml

Background:
* url demoBaseUrl
* configure logPrettyRequest = true
* configure logPrettyResponse = true

# responses default to json otherwise
* configure headers = { Accept: 'application/xml' }

Scenario: create and retrieve a cat

# create a new cat
Given path 'cats'
And request <cat><name>Billie</name></cat>
When method post
Then status 200
And match response == <cat><id>#notnull</id><name>Billie</name></cat>

* def id = /cat/id

# get by id
Given path 'cats', id
When method get
Then status 200
And match response == <cat><id>#(id)</id><name>Billie</name></cat>
And assert responseStatus == 204 || responseStatus == 200

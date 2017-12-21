Feature:

Background:
* url serverUrl + '/cats'

Scenario:
Given request { name: 'Billie' }
When method post
Then status 200
And match response == { id: 1, name: 'Billie' }

Given path '1'
When method get
Then status 200


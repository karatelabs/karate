Feature:

Background:
* url mockServerUrl + 'cats'

Scenario:
Given request { name: 'Billie' }
When method post
Then status 200
And match response == { id: 1, name: 'Billie' }
* def billie = response

Given path billie.id
When method get
Then status 200
And match response == billie

Given request { name: 'Wild' }
When method post
Then status 200
And match response == { id: 2, name: 'Wild' }
* def wild = response

Given path wild.id
When method get
Then status 200
And match response == wild

When method get
Then status 200
And match response == ([billie, wild])
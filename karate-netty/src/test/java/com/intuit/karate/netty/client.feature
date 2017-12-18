Feature:

Scenario:
Given url serverUrl
And request { name: 'Billie' }
When method post
And match response == { id: 1, name: 'Billie' }


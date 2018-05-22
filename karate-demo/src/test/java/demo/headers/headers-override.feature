Feature: scenarios can override headers set in the background

Background: 

Given url demoBaseUrl
And path 'headers'
When method get
Then status 200
And def token = response
And def time = responseCookies.time.value
* header Authorization = 'invalid'

Scenario:

* header Authorization = token + time + demoBaseUrl

Given path 'headers', token
And param url = demoBaseUrl
When method get
Then status 200

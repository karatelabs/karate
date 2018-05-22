@ignore
Feature: common routine that does not update headers
    and the caller is expected to use what is returned

Scenario:
Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

* def time = responseCookies.time.value
* def token = response

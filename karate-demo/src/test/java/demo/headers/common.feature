@ignore
Feature: common routine that updates the configured headers

Scenario:
Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

* def time = responseCookies.time.value
* def token = response
* def headersJson = { Authorization: '#(token + time + demoBaseUrl)' }
* configure headers = read('classpath:headers.js')
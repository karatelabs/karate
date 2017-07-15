@ignore
Feature: common routine that updates the configured headers and cookies

Scenario:
Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

* def time = responseCookies.time.value
* def token = response
* configure headers = read('classpath:headers.js')
# cookies are auto-configured
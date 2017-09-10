Feature: disable redirects in order to assert against the location header

Background:
* url demoBaseUrl
* configure followRedirects = false

Scenario: check if the call redirects to greeting
Given path 'redirect'
When method get
Then status 302
And match header Location == demoBaseUrl + '/greeting'

* def location = responseHeaders['Location'][0]

Given url location
When method get
Then status 200
And match response == { id: '#number', content: 'Hello World!' }

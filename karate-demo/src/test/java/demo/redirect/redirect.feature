Feature: disable redirects in order to assert against the location header

Background:
* url demoBaseUrl
* configure followRedirects = false

Scenario: check if the call redirects to greeting
Given path 'redirect'
When method get
Then status 302
And match header Location == demoBaseUrl + '/search'

* def location = responseHeaders['Location'][0]

Given url location
And param foo = 'bar'
When method get
Then status 200
And match response == { foo: ['bar'] }

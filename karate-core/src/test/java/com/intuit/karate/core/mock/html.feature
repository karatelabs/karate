Feature: html

Scenario: server response not well-formed html
* configure logPrettyResponse = true
Given url mockServerUrl
And path 'html'
When method get
Then status 200

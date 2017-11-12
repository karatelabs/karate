@ignore
Feature:

Scenario:

Given url demoBaseUrl
And path 'search'
And params params
When method get
Then status 200
And match response == expected
And match response !contains missing

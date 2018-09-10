Feature: headers with a form field and method get

Scenario:

Given url demoBaseUrl
And path 'search', 'headers'
And header Authorization = 'foo'
And form field q = 'bar'
When method get
Then status 200
And def response = karate.lowerCase(response)
And match response contains { authorization: ['foo'] }

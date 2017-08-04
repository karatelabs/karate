@ignore
Feature: create xml

Scenario:

Given url demoBaseUrl
And path 'cats'
And request { name: '#(name)' }
When method post
Then status 200

* def id = response.id

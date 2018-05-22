@ignore
Feature: re-usable feature to create a single cat

Scenario:

Given url demoBaseUrl
And path 'cats'
And request { name: '#(name)' }
When method post
Then status 200

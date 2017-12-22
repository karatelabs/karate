@ignore
Feature: using __arg

Scenario:
Given url demoBaseUrl
And path 'cats'
And request __arg
When method post
Then status 200

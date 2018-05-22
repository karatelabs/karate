@ignore
Feature: common validation routine

Scenario:
Given url demoBaseUrl
And path 'cats', id
When method get
Then status 200

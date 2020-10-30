@ignore
Feature:

Background:
* url demoBaseUrl

Scenario:
Given path 'cats'
When method get
Then status 200

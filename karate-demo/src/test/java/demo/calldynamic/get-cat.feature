@ignore
Feature:

Background:
* url demoBaseUrl

Scenario:
Given path 'cats', id
When method get
Then status 200

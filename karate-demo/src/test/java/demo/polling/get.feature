@ignore
Feature:

Scenario:
* configure report = false
Given url demoBaseUrl
And path 'greeting'
When method get
Then status 200

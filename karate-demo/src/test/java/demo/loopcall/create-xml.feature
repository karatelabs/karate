@ignore
Feature: create xml

Scenario:

Given url demoBaseUrl
And path 'cats'
And header Accept = 'application/xml'
And request <cat><name>#(name)</name></cat>
When method post
Then status 200

* def id = /cat/id

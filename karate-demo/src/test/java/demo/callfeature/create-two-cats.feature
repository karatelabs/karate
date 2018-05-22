Feature: create kittens

Background:
* url demoBaseUrl

Scenario: create kittens

# create bob cat
Given path 'cats'
And request { name: 'Bob' }
When method post
Then status 200
And def bob = response

# create wild cat
Given path 'cats'
And request { name: 'Wild' }    
When method post
Then status 200
And def wild = response

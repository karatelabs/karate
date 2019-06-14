@ignore
Feature: create kittens

Background:
* url demoBaseUrl

Scenario Outline: create kittens

# create bob cat
Given path 'cats'
And request { name: '<name>' }
When method post
Then status 200
And kittens.add(response)

Examples:
| name |
| Bob  |
| Wild |

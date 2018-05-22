@ignore
Feature: re-usable feature to create a single cat

Scenario:

# just to demo that we have two special variables __loop and __arg
# this is a useful way to refer to any list-like variables defined in the caller
# for example, you could load 2 different JSON arrays 'inputs' and 'outputs' 
# and refer to both in a data-driven test
* match __arg == kittens[__loop]

Given url demoBaseUrl
And path 'cats'
And request { name: '#(name)' }
When method post
Then status 200

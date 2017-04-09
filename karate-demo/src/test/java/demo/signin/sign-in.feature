Feature: sign-in end point

Background:
* url demoBaseUrl

Scenario: html url encoded form submit

Given path 'signin'
And form field username = 'john'
And form field password = 'secret'
When method post
Then status 200
And match response == 'success'




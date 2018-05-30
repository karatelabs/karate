@mock-servlet-todo
Feature: csrf and sign-in end point

Background:
* url demoBaseUrl

Given path 'signin', 'token'
When method get
Then status 200
And header X-CSRF-TOKEN = response

Scenario: html url encoded form submit - post
    Given path 'signin'
    And form field username = 'john'
    And form field password = 'secret'
    When method post
    Then status 200
    And match response == 'success'

Scenario: html url encoded form submit - get
    Given path 'signin'
    And form field username = 'john'
    And form field password = 'secret'
    When method get
    Then status 200
    And match response == 'success'

Scenario: html url encoded form submit - manually forming the request / NOT using 'form field'
    Given path 'signin'
    And request 'username=john&password=secret'
    And header Content-Type = 'application/x-www-form-urlencoded'
    When method post
    Then status 200
    And match response == 'success'

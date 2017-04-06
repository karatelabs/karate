@ignore
Feature: testing http patch method

Scenario:

Given url 'http://localhost:' + wiremockPort + '/v1/patch'
And request { foo: 'bar' }
When method patch
Then status 422
And match response == { success: true }
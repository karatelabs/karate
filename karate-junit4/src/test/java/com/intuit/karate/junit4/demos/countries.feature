@ignore
Feature: simple get

Scenario: storing parts of the response in a variable

Given url 'http://services.groupkt.com/country/get/all'
When method get
Then status 200
And def result = response.RestResponse.result
And match result[*].name contains 'Angola'



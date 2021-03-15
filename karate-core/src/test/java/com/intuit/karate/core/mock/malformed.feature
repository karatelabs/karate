Feature: malformed response json

Background:
* url mockServerUrl

Scenario:
Given path 'malformed'
When method get
Then status 200
And match responseType == 'string'

Given path 'jsonformed'
When method get
Then status 200
And match responseType == 'json'

Given path 'xmlformed'
When method get
Then status 200
And match responseType == 'xml'

Given path 'stringformed'
When method get
Then status 200
And match responseType == 'string'
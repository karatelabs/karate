@ignore
Feature: callSingle auth helper

Scenario: Fetch auth token once per suite
* url baseUrl
* path 'api/status'
* method get
* status 200
* def authToken = 'tok-' + response.version
* karate.log('callSingle auth fetched token:', authToken)

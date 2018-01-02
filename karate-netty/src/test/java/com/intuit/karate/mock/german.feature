Feature: german characters and response encoding

Scenario: umlauts in the response

Given url mockServerUrl
And path 'german'
When method get
Then status 200
And match response == <name>Müller</name>

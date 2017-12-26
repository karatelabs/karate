Feature: german characters and response encoding

Scenario: umlauts in the response

Given url 'http://localhost:' + wiremockPort + '/v1/german'
When method get
Then status 200
And match response == <name>Müller</name>

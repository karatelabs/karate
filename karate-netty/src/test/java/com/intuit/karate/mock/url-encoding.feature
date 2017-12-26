Feature: url encoding

Scenario: special characters should not break the http client

Given url 'http://localhost:' + wiremockPort + '/v1/encoding/�Ill~Formed@RequiredString!/'
When method get
Then status 200
And match response == { success: true }





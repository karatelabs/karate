Feature: url encoding

Scenario: special characters should not break the http client

Given url mockServerUrl + 'encoding/�Ill~Formed@RequiredString!'
When method get
Then status 200
And match response == { success: true }





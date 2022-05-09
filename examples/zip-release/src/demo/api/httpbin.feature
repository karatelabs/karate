Feature: simple requests

Scenario: simple sequence
Given url 'https://httpbin.org/anything'
And request { myKey: 'myValue' }
When method post
Then status 200
And match response contains { json: { myKey: 'myValue' } }

* path response.json.myKey
* method get
* status 200

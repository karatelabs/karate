Feature: simple requests

Scenario: simple post
* url 'https://httpbin.org'
* path 'anything'
* request { foo: 'bar' }
* method post
* status 200
* match response contains { json: { foo: 'bar' } }

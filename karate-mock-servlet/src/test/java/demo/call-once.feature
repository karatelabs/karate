Feature: to be called once before the hello-http runner

Scenario: get hello from call-once

When url demoBaseUrl
And path 'hello'
When method get
Then status 200
And match response == 'hello world'
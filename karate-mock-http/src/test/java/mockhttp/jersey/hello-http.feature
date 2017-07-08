Feature: test that works un-changed for http as well as in-process servlet / mock-http

Scenario: get hello

When url demoUrlBase
And path 'hello'
When method get
Then status 200
And match response == 'hello world'

Scenario: post cat

When url demoUrlBase
And path 'hello'
And request { name: 'Billie' }
When method post
Then status 200
And match response == { success: true }


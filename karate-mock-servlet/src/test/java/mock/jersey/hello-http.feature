Feature: test that works un-changed for http as well as in-process servlet / mock-http
Background:
* def res = karate.callSingle('classpath:demo/call-once.feature')

#Scenario: get hello
#
#When url demoBaseUrl
#And path 'hello'
#When method get
#Then status 200
#And match response == 'hello world'

Scenario: post cat

When url demoBaseUrl
And path 'hello'
And request { name: 'Billie' }
When method post
Then status 200
And match response == { success: true }

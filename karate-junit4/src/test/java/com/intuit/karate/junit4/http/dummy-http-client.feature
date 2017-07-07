Feature: swapping an http client on the fly

Scenario: configure http client class
* configure httpClientClass = 'com.intuit.karate.junit4.http.DummyHttpClient'
Given url ''
When method get
Then match response == 'hello world'


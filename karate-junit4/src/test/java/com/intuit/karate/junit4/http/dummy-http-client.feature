Feature: swapping an http client on the fly

Scenario: configure http client class
* configure httpClientClass = 'com.intuit.karate.junit4.http.DummyHttpClient'
* configure userDefined = { name: 'Smith' }
Given url 'http://foo.bar'
When method get
Then match response == 'hello Smith'


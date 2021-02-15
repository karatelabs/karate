Feature: test sending a null with the headers keyword

Scenario:

* def blah = null

Given url demoBaseUrl
And path 'search', 'headers'
And headers { foo: 'bar', blah: '#(blah)' }
When method get
Then status 200
And match response contains { foo: ['bar'] }
And match response !contains { blah: '#notnull' }
And match response contains { blah: '#notpresent' }

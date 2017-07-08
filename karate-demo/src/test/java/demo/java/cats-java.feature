Feature: cats end-point

Background:
* url demoBaseUrl
* def JavaDemo = Java.type('com.intuit.karate.demo.util.JavaDemo')

Scenario: pass json to java

Given path 'cats'
And request { name: 'Sillie' }
When method post
Then status 200

* def name = JavaDemo.getName(response)
* assert name == 'Sillie'

Given path 'cats'
When method get
Then status 200

# here the response is a json array
* def names = JavaDemo.getNames(response)
* match names contains ['Sillie']

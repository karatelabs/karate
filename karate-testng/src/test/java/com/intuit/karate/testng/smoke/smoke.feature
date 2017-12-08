Feature:

Background:
* configure afterScenario = function() { karate.log('after scenario') }
* configure afterFeature = function() { karate.log('after feature') }

Scenario: one
* print 'foo'

Scenario: two
* print 'bar'

Scenario:

* assert someConfig == 'someValue'

* def reader = function() { return karate.read('demo-json.json') }
* def fromFile = call reader
* match fromFile == { from: 'file' }

Given def text = read('demo-text.txt')
Then match text == 'Hello World!'
Then assert text == 'Hello World!'
Then match text contains 'World'




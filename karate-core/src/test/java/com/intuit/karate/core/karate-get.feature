Feature:

Scenario: shared scope
* call read('karate-get-called.feature')
* match foo == 'bar'

Scenario: isolated-scope
* def result = call read('karate-get-called.feature')
* match result.foo == 'bar'
* match karate.get('foo') == null

Scenario: shared scope js
* karate.call(true, 'karate-get-called.feature')
* match foo == 'bar'

Scenario: isolated scope js
* def result = karate.call('karate-get-called.feature')
* match result.foo == 'bar'
* match karate.get('foo') == null

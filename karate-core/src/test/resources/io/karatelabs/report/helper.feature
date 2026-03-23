@ignore
Feature: Helper for nested call tests

Scenario: Setup user and validate
* def user = { id: 1, name: 'Test User', role: 'admin' }
* print 'created user:', user.name
* karate.log('helper feature executed')
* def valid = true
* print 'validating credentials...'
* match valid == true
* def result = { authenticated: true, timestamp: java.time.Instant.now().toString() }

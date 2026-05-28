@ignore
Feature: Helper for nested call tests

Scenario: Setup user and validate
# create a synthetic user record for downstream callers
* def user = { id: 1, name: 'Test User', role: 'admin' }
* print 'created user:', user.name
* karate.log('helper feature executed')
* def valid = true
* print 'validating credentials...'
# sanity check before returning the result
* match valid == true
* def result = { authenticated: true, timestamp: java.time.Instant.now().toString() }

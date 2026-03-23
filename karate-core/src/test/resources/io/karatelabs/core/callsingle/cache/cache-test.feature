Feature: Test callSingle disk caching

Scenario: callSingle should cache to disk and return cached values
  * def auth = karate.callSingle('classpath:io/karatelabs/core/callsingle/cache/auth.feature')
  * match auth.userId == 42
  * match auth.token contains 'token-'
  # Call again - should get same cached value
  * def auth2 = karate.callSingle('classpath:io/karatelabs/core/callsingle/cache/auth.feature')
  * match auth2.token == auth.token

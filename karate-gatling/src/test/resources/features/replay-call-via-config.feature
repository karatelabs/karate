Feature: A called feature reached through a JS helper defined in karate-config.js

  Scenario: call a feature from a lambda handed to a config helper, then fail
    * print 'TOP-MARKER'
    * def out = withCall(function(){ return karate.call('classpath:features/replay-called.feature') })
    * match out == 'WRONG_VALUE_THAT_WILL_NEVER_MATCH'

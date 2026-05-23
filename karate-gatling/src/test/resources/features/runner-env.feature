Feature: Verify protocol.runner.karateEnv reaches the feature

  Scenario: env should be 'perf'
    * match karate.env == 'perf'

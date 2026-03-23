Feature: Test karate.config API

  Scenario: verify karate.config returns configure settings
    * configure readTimeout = 45000
    * configure connectTimeout = 15000
    * match karate.config.readTimeout == 45000
    * match karate.config.connectTimeout == 15000

  Scenario: verify karate.config is read-only copy
    * configure readTimeout = 30000
    * def config1 = karate.config
    * configure readTimeout = 60000
    * def config2 = karate.config
    # Original reference should still have old value (shallow copy)
    * match config1.readTimeout == 30000
    * match config2.readTimeout == 60000

Feature: Called feature that fails

  Scenario: failing callee
    * def actual = 'hello'
    * match actual == 'sub-feature-mismatch'

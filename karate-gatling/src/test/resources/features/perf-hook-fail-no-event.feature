Feature: afterScenario hook that fails without capturing a perf event

  Background:
    * configure afterScenario = function(){ karate.fail('hook failed') }

  Scenario: passing scenario, failing hook
    * match 1 == 1

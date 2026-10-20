Feature: afterScenario hook that fails after capturing a custom perf event

  Background:
    * configure afterScenario = function(){ try { karate.fail('hook failed') } finally { karate.capturePerfEvent('cleanup', 1, 2) } }

  Scenario: passing scenario, failing hook
    * match 1 == 1

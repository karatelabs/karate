Feature: afterScenario hook that captures a custom perf event and passes

  Background:
    * configure afterScenario = function(){ karate.capturePerfEvent('cleanup', 1, 2) }

  Scenario: passing scenario, passing hook
    * match 1 == 1

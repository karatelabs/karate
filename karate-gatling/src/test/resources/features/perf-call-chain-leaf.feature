Feature: Leaf feature that captures a custom perf event and then fails

  Scenario: custom event then failing match
    * karate.capturePerfEvent('leaf-event', 1, 2)
    * match 1 == 2

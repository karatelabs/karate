Feature: Custom perf event in a passing scenario

  Scenario: custom event then pass
    * def start = Date.now()
    * karate.capturePerfEvent('my-event', start, Date.now())
    * match 1 == 1

Feature: Custom perf event followed by a failing call step

  Scenario: call step fails after a custom event
    * def start = Date.now()
    * karate.capturePerfEvent('my-event', start, Date.now())
    * call read('classpath:features/perf-sub-fail.feature') { x: 1 }

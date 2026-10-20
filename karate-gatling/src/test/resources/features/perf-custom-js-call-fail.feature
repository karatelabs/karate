Feature: Custom perf event wrapping a failing karate.call

  A JS helper times a karate.call to a failing feature and captures the timing as
  a custom perf event. The failure must ride on that custom event.

  Background:
    * def timed =
      """
      function(name, fn) {
        var start = Date.now();
        try {
          return fn();
        } catch (e) {
          karate.fail('' + e);
        } finally {
          karate.capturePerfEvent(name, start, Date.now());
        }
      }
      """

  Scenario: timed karate.call fails
    * def res = timed('my-event', function(){ return karate.call('classpath:features/perf-sub-fail.feature', { x: 1 }) })

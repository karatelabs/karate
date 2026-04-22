Feature: waitUntil with JS callable (regression for 2.0.4)

  # Before the fix, waitUntil(fn) where fn is a karate-js function always
  # dispatched to waitUntil(String) — the function was stringified and the
  # resulting source shipped to Chrome via Runtime.evaluate. With no
  # karate-js scope available in V8, bindings like `locateAll`, `find`, or
  # any user `def` blew up with ReferenceError or SyntaxError. The fix
  # routes callable arguments to waitUntil(Supplier<Object>) so the
  # function is polled locally, in karate-js, where its closure resolves.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/wait'

  Scenario: waitUntil with inline arrow that uses karate-js closure
    # `tries` lives in karate-js — if the arrow were stringified and sent
    # to the browser, V8 would throw ReferenceError: tries is not defined.
    * def state = ({ tries: 0 })
    * waitUntil(() => { state.tries = state.tries + 1; return state.tries >= 2 })
    * match state.tries >= 2 == true

  Scenario: waitUntil with a named JS function reference
    * def state = ({ tries: 0 })
    * def poll = function() { state.tries = state.tries + 1; return state.tries >= 2 }
    * waitUntil(poll)
    * match state.tries >= 2 == true

  Scenario: waitUntil with arrow that calls a karate-js helper
    # `locateAll` is a karate-js root binding, not a browser global.
    # This exercises the exact pattern from the 2.0.4 regression report.
    * click('#btn-delayed')
    * waitUntil(() => locateAll('#delayed-content h2').length > 0)

  Scenario: waitUntil(String) still ships to the browser
    # Plain string expression — evaluated in V8 as before. Guards against
    # accidentally routing string args through the Supplier path.
    * waitUntil("window.asyncValue === 'ready'")

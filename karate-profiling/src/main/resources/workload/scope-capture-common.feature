Feature: callee for the scope-capture pair

  # Deliberately trivial. A bare karate.call() runs this in the CALLER's scope and
  # returns that whole scope back, so what this feature itself defines is beside the
  # point - the caller's accumulated variables are what comes back.

  Scenario:
    * def marker = 'called'

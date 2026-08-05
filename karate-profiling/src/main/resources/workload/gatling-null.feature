Feature: the emptiest feature that still runs

  Everything a Karate execution costs before the user's work begins — building the suite,
  parsing this file, evaluating karate-config.js, starting and finishing one scenario — is
  what running this measures. The single step exists because a scenario with no steps is
  not a fair floor: a real one always has at least one.

  Scenario: nothing
    * def x = 1

Feature: Screenshot Tests
  Regression guard for https://github.com/karatelabs/karate/issues/2798 —
  `screenshot()` from Gherkin must embed the image into the HTML report.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/'

  Scenario: screenshot() embeds by default
    * def png = screenshot()
    * match png == '#notnull'

  Scenario: screenshot(true) explicitly embeds
    * def png = screenshot(true)
    * match png == '#notnull'

  Scenario: screenshot(false) returns bytes without embedding
    * def png = screenshot(false)
    * match png == '#notnull'

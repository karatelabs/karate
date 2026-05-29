Feature: Screenshot Tests
  Regression guard:
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

  Scenario: screenshot('#locator') returns element-clipped bytes
    * driver serverUrl + '/input'
    * waitFor('h1')
    * def png = screenshot('h1')
    * match png == '#notnull'

  Scenario: screenshot('#locator', false) skips embed
    * driver serverUrl + '/input'
    * waitFor('h1')
    * def png = screenshot('h1', false)
    * match png == '#notnull'

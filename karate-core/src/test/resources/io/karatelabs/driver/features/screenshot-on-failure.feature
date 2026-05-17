@expect-failure
Feature: screenshotOnFailure regression
  Regression guard for https://github.com/karatelabs/karate/issues/2845 —
  when a step fails and the driver is configured with `screenshotOnFailure: true`
  (the default), a PNG screenshot must be embedded into the HTML report for
  the failed step.

  Scenario: default screenshotOnFailure embeds a PNG on step failure
    # driverConfig is inherited from karate-config.js with default screenshotOnFailure=true
    * driver serverUrl + '/'
    * waitFor('h1')
    * match 1 == 2

  Scenario: screenshotOnFailure=false suppresses the embed
    # Override the inherited config BEFORE the driver is initialized
    * def disabledConfig = karate.merge(driverConfig, { screenshotOnFailure: false })
    * configure driver = disabledConfig
    * driver serverUrl + '/'
    * waitFor('h1')
    * match 1 == 2

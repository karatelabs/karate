@cdp
Feature: Request Interception
  Tests for driver.intercept() with mock feature files and inline JS handlers.

  Background:
    * configure driver = driverConfig
    * driver 'about:blank'

  Scenario: Intercept with inline JS handler
    * driver.intercept({ patterns: [{ urlPattern: '*api/data*' }], handler: function(req){ return { status: 200, headers: { 'Content-Type': 'application/json' }, body: '{"mocked":true,"source":"handler"}' } } })
    * driver serverUrl + '/intercept'
    * click('#fetch-btn')
    * delay(500)
    * def result = text('#result')
    * match result contains 'mocked'
    * match result contains 'handler'

  Scenario: Intercept with mock feature file
    * driver.intercept({ patterns: [{ urlPattern: '*api/data*' }], mock: 'classpath:io/karatelabs/driver/features/intercept-mock.feature' })
    * driver serverUrl + '/intercept'
    * click('#fetch-btn')
    * delay(500)
    * def result = text('#result')
    * match result contains 'mocked'
    * match result contains 'feature'

  Scenario: Intercept handler returns null to continue to network
    * driver.intercept({ patterns: [{ urlPattern: '*' }], handler: function(req){ return null } })
    * driver serverUrl + '/intercept'
    * waitFor('#result')
    * match text('#result') == 'waiting'

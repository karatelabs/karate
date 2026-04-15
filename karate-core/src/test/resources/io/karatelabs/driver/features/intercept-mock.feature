@ignore
Feature: Mock for intercept test

  Scenario: pathMatches('/api/data')
    * def response = { mocked: true, source: 'feature' }

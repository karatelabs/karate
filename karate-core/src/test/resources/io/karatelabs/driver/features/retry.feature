Feature: Retry Tests
  Tests for retry() chaining with wait methods (v1 compatibility)

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/wait'

  Scenario: retry with waitUntil expression
    # window.asyncValue is set to 'ready' after 500ms in wait.html
    * retry(10, 500).waitUntil("window.asyncValue === 'ready'")

  Scenario: retry with waitFor
    # delayed-content is initially hidden, show it via button click
    * click('#btn-delayed')
    # delayed-content appears after 1000ms
    * retry(10, 500).waitFor('#delayed-content h2')

  Scenario: retry with waitForText
    * retry(10, 500).waitForText('h1', 'Wait Test')

  Scenario: retry with waitForEnabled
    # btn-enable is initially disabled, enabled after 1500ms
    * retry(10, 500).waitForEnabled('#btn-enable')

  Scenario: retry with click (implies waitFor)
    * retry(10, 500).click('#btn-text-change')
    * def text = text('#text-target')
    * match text == 'Changed Text'

  Scenario: retry no-arg uses defaults
    * retry().waitUntil("window.asyncValue === 'ready'")

  Scenario: retry with count only
    * retry(10).waitUntil("window.asyncValue === 'ready'")

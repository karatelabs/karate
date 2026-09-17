@lock=render
Feature: Retry Tests
  Tests for retry() chaining with wait methods (v1 compatibility)
  Shares the @lock=render lock with the other renderer-heavy features so two
  never starve the CDP pipeline at once. These assert on page-side timers
  (elements enabled/shown after a setTimeout); under a concurrent heavy renderer
  on a 2-vCPU runner those timers fire late and even the retry budget here can
  lapse before the element is ready.

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

  Scenario: element retry waits for an element that appears after the lookup
    # appended to the page only after 800ms, so the lookup below finds nothing
    * script("setTimeout(function(){ var p = document.createElement('p'); p.id = 'late'; p.textContent = 'late text'; document.body.appendChild(p) }, 800)")
    * def late = locate('#late')
    * match late.exists() == false
    * def lateRetry = late.retry(10, 500)
    * lateRetry.waitFor()
    * match lateRetry.present == true
    * match lateRetry.exists() == true
    * match lateRetry.text() == 'late text'
    * match lateRetry.html() contains 'late text'

  Scenario: element retry waits for enabled before the click
    * locate('#btn-enable').retry(10, 500).waitForEnabled().click()
    * match text('#result') == 'Button was clicked!'

  Scenario: retry no-arg uses defaults
    * retry().waitUntil("window.asyncValue === 'ready'")

  Scenario: retry with count only
    * retry(10).waitUntil("window.asyncValue === 'ready'")

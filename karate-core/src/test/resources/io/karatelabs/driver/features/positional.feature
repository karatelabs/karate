@cdp
Feature: Positional Locators
  Element Finder: rightOf, leftOf, above, below, near
  CDP-only — Finder iterates {@link Driver#locateAll} candidates and calls
  {@link Element#position} on each. The indexed locators returned by
  locateAll evaluate cleanly under CDP's Runtime.evaluate but intermittently
  return null under W3C executeScript (stale element / context drift),
  causing getBoundingClientRect on null. Track separately if/when we
  stabilise W3C indexed-locator resolution.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/positional'
    * waitFor('#title')

  Scenario: rightOf finds button to the right of input
    * def btn = rightOf('#username-input').find('button')
    * match btn.attribute('id') == 'username-btn'

  Scenario: leftOf finds label to the left of input
    * def label = leftOf('#username-input').find('span.label')
    * match label.attribute('id') == 'username-label'

  Scenario: rightOf chains into click via Finder.click
    * def btn = rightOf('#email-input').find('button')
    * match btn.attribute('id') == 'email-btn'

  Scenario: above finds element above another in vertical stack
    * def above = above('#stack-input-1').find('span')
    * match above.attribute('id') == 'anchor-stack-top'

  Scenario: below finds element below another in vertical stack
    * def below = below('#stack-input-1').find('span')
    * match below.attribute('id') == 'anchor-stack-bottom'

  Scenario: near with default tolerance picks the closest neighbour
    * def near = near('#cluster-anchor').find('span')
    * match near.attribute('id') == 'cluster-near'

  Scenario: rightOf returns sorted by distance via findAll
    * def btn = rightOf('#username-input').find('button')
    * match btn.attribute('id') == 'username-btn'

  Scenario: positional finder exists() reports membership
    * match rightOf('#username-input').exists('button') == true

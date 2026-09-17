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
    * script("document.getElementById('email-btn').onclick = function() { window.clickedId = this.id }")
    * def btn = rightOf('#email-input').click('button')
    * match btn.attribute('id') == 'email-btn'
    * match script('window.clickedId') == 'email-btn'

  Scenario: below finds the input and the returned element accepts input
    * def el = below('#anchor-stack-top').find('input')
    * el.clear().input('typed')
    * match value('#stack-input-1') == 'typed'

  Scenario: above finds element above another in vertical stack
    * def above = above('#stack-input-1').find('span')
    * match above.attribute('id') == 'anchor-stack-top'

  Scenario: below finds element below another in vertical stack
    * def below = below('#stack-input-1').find('span')
    * match below.attribute('id') == 'anchor-stack-bottom'

  Scenario: near with default tolerance picks the closest neighbour
    * def near = near('#cluster-anchor').find('span')
    * match near.attribute('id') == 'cluster-near'

  Scenario: findAll returns only the matches satisfying the positional constraint
    * def btns = rightOf('#username-input').findAll('button')
    * match btns.length == 1
    * match btns[0].attribute('id') == 'username-btn'

  Scenario: within widens the near tolerance so findAll picks up the far neighbour
    * def close = near('#cluster-anchor').findAll('span')
    * match close.length == 1
    * match close[0].attribute('id') == 'cluster-near'
    * def wider = near('#cluster-anchor').within(500)
    * def wide = wider.findAll('span')
    * assert wide.length > close.length
    * match wide[0].attribute('id') == 'cluster-near'
    * match wider.exists('#cluster-far') == true

  Scenario: positional finder exists() reports membership
    * match rightOf('#username-input').exists('button') == true

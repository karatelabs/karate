@lock=oopif
Feature: Out-of-Process Iframe (OOPIF) Support
  Cross-origin frame switching against an iframe served from a different
  "site" (different eTLD+1) than the parent page. With --site-per-process,
  Chrome places such iframes in a separate process — exactly like a real
  third-party iframe (Stripe, PayPal, etc.).

  The parent page is served via host.testcontainers.internal; the iframe
  content is loaded from host.docker.internal — both pointing at the same
  TestPageServer, but with different eTLD+1 hostnames so Chrome isolates them.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/oopif'
    * switchFrame(null)

  Scenario: Switch to cross-origin iframe and back
    * def title = text('h1')
    * match title == 'OOPIF Parent Page'
    * script("document.getElementById('cross-origin-frame').src = '" + crossOriginUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * def heading = text('h2')
    * match heading == 'OOPIF Frame Content'
    * switchFrame(null)
    * def parentTitle = text('h1')
    * match parentTitle == 'OOPIF Parent Page'

  Scenario: Read element text inside cross-origin iframe
    * script("document.getElementById('cross-origin-frame').src = '" + crossOriginUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * def frameText = text('#frame-text')
    * match frameText == 'This content is inside the cross-origin iframe.'
    * switchFrame(null)

  Scenario: Execute script inside cross-origin iframe
    * script("document.getElementById('cross-origin-frame').src = '" + crossOriginUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * def value = script('window.frameValue')
    * match value == 'oopif-frame-data'
    * switchFrame(null)
    * def parentValue = script('window.parentValue')
    * match parentValue == 'parent-data'

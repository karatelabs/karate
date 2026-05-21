@lock=oopif
Feature: Out-of-Process Iframe (OOPIF) Support
  Cross-origin frame switching where the iframe is served over HTTPS
  from a different origin than the HTTP parent page, triggering
  Chrome's out-of-process iframe (OOPIF) behavior.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/oopif'
    * switchFrame(null)

  Scenario: Switch to cross-origin HTTPS iframe and back
    # Verify parent page content
    * def title = text('h1')
    * match title == 'OOPIF Parent Page'
    # Load cross-origin iframe from HTTPS server
    * script("document.getElementById('cross-origin-frame').src = '" + httpsServerUrl + "/oopif-frame'")
    * delay(1000)
    # Switch to the cross-origin iframe
    * switchFrame('#cross-origin-frame')
    * def heading = text('h2')
    * match heading == 'OOPIF Frame Content'
    # Switch back to parent frame
    * switchFrame(null)
    * def parentTitle = text('h1')
    * match parentTitle == 'OOPIF Parent Page'

  Scenario: Read element text inside cross-origin iframe
    * script("document.getElementById('cross-origin-frame').src = '" + httpsServerUrl + "/oopif-frame'")
    * delay(1000)
    * switchFrame('#cross-origin-frame')
    * def frameText = text('#frame-text')
    * match frameText == 'This content is inside the cross-origin iframe.'
    * switchFrame(null)

  Scenario: Execute script inside cross-origin iframe
    * script("document.getElementById('cross-origin-frame').src = '" + httpsServerUrl + "/oopif-frame'")
    * delay(1000)
    * switchFrame('#cross-origin-frame')
    * def value = script('window.frameValue')
    * match value == 'oopif-frame-data'
    * switchFrame(null)
    * def parentValue = script('window.parentValue')
    * match parentValue == 'parent-data'

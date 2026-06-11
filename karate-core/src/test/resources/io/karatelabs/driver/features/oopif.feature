@cdp @lock=oopif
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

  @lock=tabs
  Scenario: Cross-origin iframe reachable after switching tabs
    # switchPage() drives the tab through a fresh CDP session, and OOPIF
    # auto-attach is session-scoped — the driver must re-arm it on every
    # session switch, otherwise cross-origin frames silently stop attaching
    # and switchFrame fails with "could not find frame". Seen in the wild
    # with payment-provider card-field iframes on pooled drivers reused
    # after a scenario that switched or closed tabs.
    # (also holds the tabs lock: the popup opened here would skew the
    # page-count assertions in the tab tests if they ran concurrently)
    * script("window.__popup = window.open('" + crossOriginUrl + "/oopif'), null")
    * delay(500)
    * switchPage(crossOriginUrl + '/oopif')
    * waitFor('#cross-origin-frame')
    # in this tab the parent is served from the cross-origin host, so the
    # primary server URL is the "foreign" origin that promotes the iframe
    # to an OOPIF
    * script("document.getElementById('cross-origin-frame').src = '" + serverUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * def heading = text('h2')
    * match heading == 'OOPIF Frame Content'
    * switchFrame(null)
    # switching back is yet another fresh session — OOPIFs must re-attach
    # in the original tab too
    * switchPage(serverUrl + '/oopif')
    * script("document.getElementById('cross-origin-frame').src = '" + crossOriginUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * def frameText = text('#frame-text')
    * match frameText == 'This content is inside the cross-origin iframe.'
    * switchFrame(null)
    # close the popup so later scenarios see a clean slate
    * script('window.__popup.close(), null')
    * delay(200)

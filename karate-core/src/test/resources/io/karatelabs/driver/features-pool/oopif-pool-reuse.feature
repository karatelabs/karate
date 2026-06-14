@cdp
Feature: OOPIF auto-attach survives pooled-driver reuse across scenarios

  Reproduces the cross-scenario failure that bit pooled drivers in parallel
  suites. Target.setAutoAttach is armed once at init on the tab's root session.
  Every switchPage()/close() drives a DIFFERENT tab through a fresh "flattened"
  CDP session where auto-attach defaults OFF. Unless the driver re-arms
  auto-attach on each session switch, cross-origin iframes (OOPIFs) in that tab
  stop firing Target.attachedToTarget and switchFrame() fails with
  "could not find frame".

  The poison persists across the pool: PooledDriverProvider.resetDriver() only
  navigates to about:blank and clears cookies between scenarios — it does NOT
  re-create the CDP session — so a LATER scenario that reuses the same pooled
  driver inherits the unarmed session. Crucially, the earlier scenario must
  leave the driver active on a NON-root tab (an opened popup); switching back to
  the root tab would mask the bug because that tab's auto-attach is still live.

  Run by OopifPooledReuseTest with a pool of one (parallel 1) so scenario 2
  deterministically reuses the driver scenario 1 left on a fresh session.

  Background:
    * configure driver = driverConfig

  Scenario: a tab switch leaves the driver active on a fresh, non-root session
    * driver serverUrl + '/oopif'
    * waitFor('#cross-origin-frame')
    # open a second tab and switch into it — switchPage drives this popup through
    # a brand-new flattened session where auto-attach is OFF. Deliberately do NOT
    # switch back: the driver is released to the pool sitting on this poisoned
    # session, exactly as it would be mid-suite after a tab-juggling scenario.
    * script("window.open('" + serverUrl + "/index.html'), null")
    * delay(500)
    * switchPage('/index.html')
    * match driver.url contains '/index.html'

  Scenario: cross-origin iframe still attaches on the reused pooled driver
    # This scenario never opens or switches tabs. It navigates the tab it
    # inherited (the popup scenario 1 left active) and exercises an OOPIF there.
    # It can only pass if that inherited session still has auto-attach armed —
    # i.e. the driver re-armed it on the earlier switch.
    * driver serverUrl + '/oopif'
    * switchFrame(null)
    * script("document.getElementById('cross-origin-frame').src = '" + crossOriginUrl + "/oopif-frame'")
    * switchFrame('#cross-origin-frame')
    * match text('#frame-text') == 'This content is inside the cross-origin iframe.'
    * switchFrame(null)
    * match text('h1') == 'OOPIF Parent Page'

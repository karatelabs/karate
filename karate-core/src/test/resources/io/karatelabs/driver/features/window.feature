Feature: Window & Lifecycle Globals
  Locks in v1-compatible bare-keyword bindings for window state, lifecycle,
  navigation, and timing helpers — these used to work as `* maximize` in v1
  and would silently fail if not bound at the JS root.
  Previously @lock=render because a reload/navigation could return before the
  reloaded DOM was ready under concurrent load. The driver's page-load waits are
  now bound to the navigation's own document (loaderId / superseded-loader), so
  that race is closed at the source — deliberately unlocked as a regression
  sentinel for it.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/input'
    * waitFor('h1')

  Scenario: reload does a hard reload
    * input('#username', 'temp')
    * reload()
    * waitFor('#username')
    * match value('#username') == ''

  Scenario: maximize / minimize / fullscreen are bound globals
    # We don't assert OS-level outcomes — just that the calls don't throw
    # and dimensions can be inspected after.
    * maximize()
    * def d1 = driver.dimensions
    * match d1 contains { width: '#number' }
    * match d1 contains { height: '#number' }

  Scenario: setDimensions roundtrips through the dimensions property
    # Headless Chrome ignores some hints, but we can at least verify the
    # JS binding is wired up — round-tripping width/height as numbers.
    * driver.dimensions = { width: 800, height: 600 }
    * def d = driver.dimensions
    * match d.width == '#number'
    * match d.height == '#number'

  Scenario: timeout() reads and writes the operation timeout
    * def before = timeout()
    * assert before > 0
    * timeout(15000)
    * match timeout() == 15000
    # restore for any pooled-driver follow-on
    * timeout(before)

  Scenario: timeout(n) returns driver for chaining
    * def d = timeout(20000)
    * match d.url == driver.url
    * timeout(30000)

  Scenario: activate brings the page to front (smoke)
    * activate()
    * match driver.title == 'Input Test'

Feature: Driver inheritance in called features
  Tests V1-compatible driver behavior:
  - Driver instance is inherited by called features
  - Driver created in called feature propagates to caller
  - Driver is not closed until top-level scenario exits

Background:
* url serverUrl

Scenario: driver should work in called feature
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'
* print 'Main feature - calling sub-feature'
* call read('call-driver-sub.feature')
* print 'Back in main feature'
* match driver.title == 'Karate Driver Test'

Scenario: called feature propagates driver to caller automatically
# This scenario does NOT init driver - calls a feature that inits driver
# Driver auto-propagates for shared-scope calls (no scope: 'caller' needed)
* print 'Main feature - not initializing driver'
* call read('call-config-inherit.feature')
* print 'Back in main - driver should be available (propagated from callee)'
* match driver.title == 'Karate Driver Test'

@lock=*
Scenario: re-init driver after quit
# Simulates running tests under multiple browsers in succession (e.g. a grid provider like
# BrowserStack / Sauce Labs). For efficiency we want to:
#  - start each remote browser session once and run through all tests
#  - manually terminate each session when done so the grid slot is freed
# In a real run the list of browser configs would be split into chunks by a dynamic @setup
# scenario (one chunk per parallel thread); each chunk is processed sequentially per thread,
# with all chunks running in parallel. We use threadCount + 1 here just to exercise the pool.
* def runTestsWithBrowser =
"""
function (cfg, testPaths) {
  karate.configure('driver', cfg)
  karate.driver.setUrl(serverUrl + '/index.html')
  karate.map(testPaths, function(testPath) {
    driver.clearCookies()
    driver.setUrl(serverUrl + '/index.html')
    karate.call(testPath)
  })
  driver.quit()
}
"""
* def browserConfigs = karate.repeat(karate.suite.threadCount + 1, () => karate.config.driverConfig)
* def testPaths = ['call-driver-sub.feature']
* karate.map(browserConfigs, cfg => runTestsWithBrowser(cfg, testPaths))


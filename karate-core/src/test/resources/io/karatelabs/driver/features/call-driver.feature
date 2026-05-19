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
# Simulate running tests under multiple different browsers (e.g. grid provider like BrowserStack, Sauce Labs, etc...).
# For efficiency, we want to:
#  - start each remote browser session only once and run through all tests
#  - manually terminate each remote browser session when all tests are complete to free up the grid slot
* def runTestsWithBrowser =
"""
function (cfg, testPaths) {
  if (!karate.get('driver') || driver.isTerminated()) {
    karate.configure('driver', cfg)
    karate.set('driver', karate.scenario.runtime.getDriver())
  }
  karate.map(testPaths, (testPath) => {
    driver.clearCookies()
    driver.setUrl(serverUrl + '/index.html')
    karate.call(testPath)
  })
  driver.quit()
}
"""
# For simplicity we'll use the current number of parallel threads to ensure we excersize the available driver pool.
# In a realworld case the list of browser configs would be split into even chunks in a dynamic @setup scenario where
# the number of chunks matches the number of desired parallel threads / simultaneous grid slots to consume. We'd then
# process each individual chunk sequentially but all available chunks in parallel.
* def threadCount = karate.scenario.runtime.getFeatureRuntime().getSuite().threadCount
* def browserConfigs = karate.repeat(threadCount + 1, () => karate.config.driverConfig)
* def testPaths = ['call-driver-sub.feature']
* print 'Main feature - calling sub-features with pre-configured drivers'
* karate.map(browserConfigs, (cfg) => runTestsWithBrowser(cfg, testPaths))
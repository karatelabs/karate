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

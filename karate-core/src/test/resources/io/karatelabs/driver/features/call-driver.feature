Feature: Driver inheritance in called features
  Tests V1-compatible driver behavior:
  - Driver instance is inherited by called features
  - Driver config is inherited by called features
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

Scenario: called feature with scope caller propagates driver up
# This scenario does NOT init driver - calls a feature that inits driver
# The called feature uses scope: 'caller' so driver propagates back
* print 'Main feature - not initializing driver'
* call read('call-config-inherit.feature')
* print 'Back in main - driver should be available (propagated from callee)'
* match driver.title == 'Karate Driver Test'

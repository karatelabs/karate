@ignore
Feature: Orchestration feature (simulating v1 00.feature)
  Initializes driver using inherited driverConfig and calls sub-features.

Background:
* def testLabel = karate.get('testLabel')
* print 'orchestration: testLabel =', testLabel
# Driver is auto-initialized using inherited driverConfig (from karate-config.js via caller)
* driver serverUrl + '/index.html'
* print 'orchestration: driver initialized, title =', driver.title

Scenario: Call sub-features with inherited driver
* match driver.title == 'Karate Driver Test'

# Call sub-feature - driver should be inherited
* print 'orchestration: calling outline-sub.feature'
* call read('outline-sub.feature')
* print 'orchestration: back from sub, title =', driver.title
* match driver.title == 'Karate Driver Test'

# Verify driver still works after call
* driver serverUrl + '/navigation.html'
* match driver.title == 'Navigation Test'
* print 'orchestration: completed successfully'

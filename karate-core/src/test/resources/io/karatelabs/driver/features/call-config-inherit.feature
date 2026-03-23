@ignore
Feature: Test scope: caller - driver propagates to caller

Background:
# Use scope: 'caller' so driver propagates back to caller after call returns
* def driverWithScope = karate.merge(driverConfig, { scope: 'caller' })
* configure driver = driverWithScope

Scenario: Init driver with caller scope
# This feature is called by call-driver.feature
# Driver should propagate back to caller when this scenario ends
* print 'call-config-inherit: initializing driver with scope: caller'
* print 'call-config-inherit: serverUrl =', serverUrl
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'
* print 'call-config-inherit: driver initialized, will propagate to caller'

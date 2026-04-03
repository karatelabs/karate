@ignore
Feature: Called feature initializes driver - propagates to caller automatically

Background:
# No scope: 'caller' needed - shared-scope calls auto-propagate the driver
* configure driver = driverConfig

Scenario: Init driver in called feature
# This feature is called by call-driver.feature
# Driver automatically propagates back to caller for shared-scope calls
* print 'call-config-inherit: initializing driver'
* print 'call-config-inherit: serverUrl =', serverUrl
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'
* print 'call-config-inherit: driver initialized, will propagate to caller'

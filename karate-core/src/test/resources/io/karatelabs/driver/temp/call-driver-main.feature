Feature: Test driver inheritance with real browser

Scenario: driver should work in called feature
* print 'Main feature - serverUrl:', serverUrl
* driver serverUrl + '/index.html'
* print 'Main feature - driver title:', driver.title
* match driver.title == 'Main Page'
* print 'Main feature - calling sub-feature'
* call read('call-driver-sub.feature')
* print 'Back in main feature'
* print 'Main feature - driver title after call:', driver.title

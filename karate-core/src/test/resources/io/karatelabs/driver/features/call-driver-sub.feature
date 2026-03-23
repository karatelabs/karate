@ignore
Feature: Sub-feature called with driver

Scenario: use inherited driver
* print 'Sub-feature: checking driver is available'
* print 'Driver title:', driver.title
* match driver.title == 'Karate Driver Test'
* driver serverUrl + '/wait.html'
* match driver.title == 'Wait Test'
* print 'Sub-feature: navigated to wait page, now navigating back'
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'

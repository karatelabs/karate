@ignore
Feature: Sub-feature called with driver

Scenario: use inherited driver
* print 'Sub-feature: checking driver is available'
* print 'Sub-feature: driver =', driver
* print 'Sub-feature: Driver title:', driver.title
* match driver.title == 'Main Page'
* def serverUrl = karate.properties['serverUrl']
* driver serverUrl + '/wait.html'
* match driver.title == 'Wait Page'
* print 'Sub-feature: navigated to wait page'

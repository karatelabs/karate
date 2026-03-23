@ignore
Feature: Sub-feature for outline test (level 2)
  Uses inherited driver from orchestration feature.

Scenario: Use inherited driver
* print 'outline-sub: checking inherited driver'
* print 'outline-sub: driver.title =', driver.title
* match driver.title == 'Karate Driver Test'

# Navigate to different page using inherited driver
* driver serverUrl + '/wait.html'
* match driver.title == 'Wait Test'
* print 'outline-sub: navigated to wait page'

# Navigate back so orchestration can verify
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'
* print 'outline-sub: completed, returned to index page'

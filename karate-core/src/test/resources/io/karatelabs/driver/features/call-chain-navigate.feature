@ignore
Feature: Navigate step (reusable called feature)

Scenario: navigate to input page
# Uses inherited driver from caller - no driver init needed
* driver serverUrl + '/input'
* waitFor('#username')
* match driver.title == 'Input Test'

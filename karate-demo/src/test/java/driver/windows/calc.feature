Feature:

  Background:
    * def session = { desiredCapabilities: { app: 'Microsoft.WindowsCalculator_8wekyb3d8bbwe!App' } }

    Scenario:
      Given driver { type: 'winappdriver', webDriverSession: '#(session)' }
      And driver.click('One')
      And driver.click('Plus')
      And driver.click('Seven')
      When driver.click('Equals')
      Then match driver.text('@CalculatorResults') contains '8'

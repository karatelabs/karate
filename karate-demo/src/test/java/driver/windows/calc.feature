Feature:

  Background:
    * configure driver = { type: 'winappdriver' }

    Scenario:
      Given driver { app: 'Microsoft.WindowsCalculator_8wekyb3d8bbwe!App' }
      And driver.click('One')
      And driver.click('Plus')
      And driver.click('Seven')
      When driver.click('Equals')
      Then match driver.text('@CalculatorResults') contains '8'

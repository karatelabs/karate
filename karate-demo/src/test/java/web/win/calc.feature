Feature:

  Background:
    * configure driver = { type: 'winappdriver', port: 4727, executable: 'C:/Program Files (x86)/Windows Application Driver/WinAppDriver' }

    Scenario:
      Given driver { app: 'Microsoft.WindowsCalculator_8wekyb3d8bbwe!App' }
      And click One
      And click Plus
      And click Seven
      When click Equals
      Then match driver.text('@CalculatorResults') contains '8'

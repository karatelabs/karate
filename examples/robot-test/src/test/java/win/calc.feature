Feature: windows calculator

Scenario:
* robot { window: 'Calculator', fork: 'calc', highlight: true, highlightDuration: 500 }
* click('Clear')
* click('One')
* click('Plus')
* click('Two')
* click('Equals')
* match locate('#CalculatorResults').name == 'Display is 3'
* screenshot()
* click('Close Calculator')
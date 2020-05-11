Feature: windows calculator

Scenario:
* robot { window: 'Calculator', fork: 'calc', highlight: true }
* robot.click('Clear')
* robot.click('One')
* robot.click('Plus')
* robot.click('Two')
* robot.click('Equals')
* match robot.locate('#CalculatorResults').name == 'Display is  3 '
* robot.screenshot()

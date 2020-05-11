Feature: windows calculator

Scenario:
* robot { window: 'Calculator', fork: 'calc', highlight: true }
* robot.click('One')
* robot.click('Two')
* robot.click('Three')
* robot.screenshot()

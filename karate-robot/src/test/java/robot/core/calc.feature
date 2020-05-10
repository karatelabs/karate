Feature: windows calculator

Scenario:
* robot { window: 'Calculator', fork: 'calc' }
* robot.click('One')
* robot.click('Two')
* robot.click('Three')
* robot.screenshot()

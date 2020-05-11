Feature: browser + robot test

Scenario:
# * karate.exec('Chrome')
* robot { window: '^Simulator', highlight: true }
* robot.click('iphone-click.png')

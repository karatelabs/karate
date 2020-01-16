Feature: browser + robot test

Scenario:
# * karate.exec('Chrome')
* robot { app: '^Simulator', highlight: true }
* robot.click('src/test/resources/iphone-click.png')

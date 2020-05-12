Feature: browser + robot test

Scenario:
# * karate.exec('Chrome')
* robot { window: '^Simulator', highlight: true }
* click('iphone-click.png')

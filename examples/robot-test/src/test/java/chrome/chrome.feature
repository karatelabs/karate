Feature: browser + robot test

Scenario:
# * karate.exec('Chrome')
# or make sure Chrome is open
* robot { window: '^Chrome', highlight: true }
* input(Key.META + 't')
* input('karate dsl' + Key.ENTER)
* click('tams.png')
* delay(2000)
* screenshot()


Feature: mac - robot and chrome

Scenario:
# * karate.exec('Chrome')
# or make sure Chrome is open
* robot { window: '^Chrome', highlight: true, highlightDuration: 500 }
* input(Key.META + 't')
* input('karate dsl' + Key.ENTER)
* waitFor('tams.png').click()
* delay(2000)
* screenshot()


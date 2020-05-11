Feature: browser + robot test

Scenario:
# make sure chrome is running
# * karate.exec('Chrome')
# on windows you may need to change this to "New Tab"
* robot { window: '^Chrome', highlight: true }
# on windows use Key.CONTROL
* robot.input(Key.META + 't')
* robot.input('karate dsl' + Key.ENTER)
# if this does not work try to re-create the PNG image
* robot.click('tams.png')

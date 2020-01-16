Feature: browser + robot test

Scenario:
# * karate.exec('Chrome')
* robot { app: '^Chrome', highlight: true }
* robot.input(Key.META, 't')
* robot.input('karate dsl' + Key.ENTER)
* robot.click('src/test/resources/tams.png')

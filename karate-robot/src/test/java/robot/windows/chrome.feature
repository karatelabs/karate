Feature:

Background:
* print 'background'

Scenario:
# * karate.exec('Chrome')
* robot '^Chrome'
* robot.input(Key.META, 't')
* robot.input('karate dsl' + Key.ENTER)
* robot.find('src/test/resources/tams.png').click()

Feature:

  Scenario:
    * call read('@stub')
    * print 'first'
    * def result = 'first'

  Scenario:
    * print 'second'
    * def result = 'second'

  @stub @ignore
  Scenario:
    * print 'called'

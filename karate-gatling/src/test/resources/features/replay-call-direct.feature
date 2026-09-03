Feature: A print, a called feature, then a failure

  Scenario: call a feature from a step, then fail
    * print 'TOP-MARKER'
    * call read('classpath:features/replay-called.feature')
    * match 1 == 2

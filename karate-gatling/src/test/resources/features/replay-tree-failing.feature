Feature: a callee that prints and then fails

  Scenario: failing
    * print 'L1-FAIL'
    * match 1 == 2

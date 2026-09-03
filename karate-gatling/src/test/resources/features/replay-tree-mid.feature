Feature: a callee that itself calls, prints only

  Scenario: mid
    * print 'L1'
    * call read('classpath:features/replay-tree-leaf.feature')

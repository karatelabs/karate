Feature: the root of a call tree, prints only

  Scenario: top
    * print 'L0'
    * call read('classpath:features/replay-tree-mid.feature')
    * call read('classpath:features/replay-tree-empty.feature')
    * call read('classpath:features/replay-tree-leaf.feature') [{ n: 1 }, { n: 2 }]
    * call read('classpath:features/replay-tree-failing.feature')

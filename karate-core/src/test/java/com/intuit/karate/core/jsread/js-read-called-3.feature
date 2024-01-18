Feature:

#see https://github.com/karatelabs/karate/pull/1436
Background:
    * def called = call read('../utils-reuse-common.feature')

Scenario:
    * print 'arg: ' + __arg
    * match __arg == "#present"
    * match __arg == "#notnull"
    * match __arg == { 'foo': 'bar' }
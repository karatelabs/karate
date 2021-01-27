Feature:

#see https://github.com/intuit/karate/pull/1436
Background: js-read-called-3 is calling a feature in background and assigning it's result.
    * def called = call read('../utils-reuse-common.feature')

Scenario:
    * print 'arg: ' + __arg
    * match __arg == "#present"
    * match __arg == "#notnull"
    * match __arg == { 'foo': 'bar' }
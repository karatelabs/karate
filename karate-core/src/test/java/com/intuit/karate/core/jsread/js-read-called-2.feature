Feature:

Background:
    #* call read('js-read-3.json')
    * call read('../utils-reuse-common.feature')

Scenario:
    * print 'arg: ' + __arg
    * match __arg == "#present"
    * match __arg == "#notnull"
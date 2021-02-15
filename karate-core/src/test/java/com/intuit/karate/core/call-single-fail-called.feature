Feature:

Background:
* def fun = function(){ throw 'fail-called'  }
* fun()

Scenario:
* print 'in fail-called'

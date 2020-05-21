Feature: will js calls show up in html report 

Background:
* def fun = function(){ karate.call('called-js.feature') }

Scenario:
* print 'before call'
* fun()
* print 'after call'
* print 'before js common'
* call myCommon
* print 'after js common'


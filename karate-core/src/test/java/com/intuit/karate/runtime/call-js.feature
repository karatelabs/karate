Feature: will a js call show up in the report

Background:
* def fun = function(){ karate.call('call-js-called.feature') }

Scenario:
* print 'before call'
* fun()
* print 'after call'

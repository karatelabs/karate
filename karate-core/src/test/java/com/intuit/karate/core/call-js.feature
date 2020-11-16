Feature: will a js call show up in the report

Background:
* def fun = function(){ return karate.call('call-js-called.feature') }

Scenario:
* print 'before call'
* call fun
* print 'after call'

Feature:

Background:
* match __arg == { foo: 'bar' }
* call read('call-arg-common.feature')

Scenario:
* print 'in scenario'
* match __arg == { foo: 'bar' }

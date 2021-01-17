Feature:

Background:
* print 'in background'

Scenario:
* def params = { 'foo': 'bar' }
* call read('call-arg-called.feature') params

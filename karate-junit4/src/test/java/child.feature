Feature: child

Scenario:
  * print 'child called with param =', param
  * def tmp = typeof param === 'undefined' ? 'default' : param
  * def value = 'child-' + tmp

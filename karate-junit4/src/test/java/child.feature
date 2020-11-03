Feature: child

Scenario:
  * print 'child called with param =', karate.get('param')
  * def tmp = typeof param === 'undefined' ? 'default' : param
  * def value = 'child-' + tmp

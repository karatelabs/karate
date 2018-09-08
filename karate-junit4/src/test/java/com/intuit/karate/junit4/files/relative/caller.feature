Feature:

Scenario:
* print 'in caller'
* call read('classpath:common.feature')
* call read('called1.feature')
* match success == true

Feature:

Scenario: relative
* print 'in caller'
* call read('classpath:common.feature')
* call read('called1.feature')
* match success == true

Scenario: shared scope, dynamic name
* def common = 'common.feature'
* call read('classpath:' + common)
* call read('classpath:' + common) { scope: 'shared' }

Scenario: isolated scope, dynamic
* def common = 'common.feature'
* def result = call read('classpath:' + common)
* def result = call read('classpath:' + common) { scope: 'isolated' }


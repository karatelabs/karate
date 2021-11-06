Feature:

Scenario:
* def result = karate.call('classpath:com/intuit/karate/core/jscall/dummy.feature')
* utils.sayHello()
* karate.call('js-called.feature')
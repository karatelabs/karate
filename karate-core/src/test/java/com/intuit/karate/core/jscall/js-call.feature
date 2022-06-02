Feature:

Scenario:
* def result = karate.call('classpath:com/intuit/karate/core/jscall/dummy.feature')
* utils.sayHello()
# TODO broke after graal upgrade to 22
# * karate.call('js-called.feature')
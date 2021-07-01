Feature:

Scenario:
* print 'before configure headers'
* configure headers = read('headers.js')
* def message = 'from common'
* def HelloOnce = Java.type('com.intuit.karate.core.parallel.Hello')
* def sayHelloOnce = function(name){ return 'hello ' + name }

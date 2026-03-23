Feature: Called once per feature - sets up headers and Java interop

  Scenario:
    * print 'callonce: setting up headers and Java class'
    * configure headers = read('http-headers.js')
    * def message = 'from callonce'
    * def HelloOnce = Java.type('io.karatelabs.core.parallel.Hello')
    * def sayHelloOnce = function(name){ return 'hello ' + name }

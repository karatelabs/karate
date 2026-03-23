Feature: Called via callSingle from karate-config.js

  Scenario:
    * url serverUrl
    * path 'fromconfig'
    * method get
    * status 200
    * match response == { message: 'from config' }
    * def HelloSingle = Java.type('io.karatelabs.core.parallel.Hello')

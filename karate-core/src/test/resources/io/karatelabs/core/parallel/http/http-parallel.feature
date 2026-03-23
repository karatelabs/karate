Feature: Parallel HTTP with configure, callonce, callSingle, Java interop

  # Tests combined parallel scenarios:
  # - configure headers (from callonce)
  # - configure cookies (set here)
  # - callonce with Java.type
  # - callSingle from config (HelloConfigSingle, sayHello)
  # - HTTP calls with headers/cookies
  # - karate-base.js functions

  Background:
    * url serverUrl
    * match message == 'from config'
    * callonce read('http-callonce.feature')
    * match message == 'from callonce'
    # cookies normalized - reading JS function should work (will be null if not present)
    * configure cookies = read('http-cookies.js')

  Scenario: one - HTTP with headers from callonce
    * call sayHelloOnce 'one'
    * path 'one'
    * method get
    * status 200
    * match response == { one: '#string' }
    # Verify Java interop from callonce and callSingle
    * match HelloConfigSingle.sayHello('world') == 'hello world'
    * match HelloOnce.sayHello('world') == 'hello world'
    * match sayHello('world') == 'hello world'
    * match sayHelloOnce('world') == 'hello world'
    # Verify karate-base.js function
    * match baseFunction('test1') == 'base:test1'

  Scenario: two - HTTP with headers and cookies
    * call sayHelloOnce 'two'
    * path 'two'
    * method get
    * status 200
    * match response == { two: '#string' }
    # Verify Java interop still works
    * match HelloConfigSingle.sayHello('world') == 'hello world'
    * match HelloOnce.sayHello('world') == 'hello world'
    * match baseFunction('test2') == 'base:test2'

  Scenario: three - verify callSingle from feature
    * call sayHelloOnce 'three'
    * path 'three'
    * method get
    * status 200
    * match response == { three: '#string' }
    # callSingle from feature level
    * def result = karate.callSingle('http-callsingle-feature.feature')
    * match result.response == { message: 'from feature' }
    * match sayHello('world') == 'hello world'

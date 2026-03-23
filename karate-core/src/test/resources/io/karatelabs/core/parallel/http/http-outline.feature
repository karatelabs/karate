Feature: Dynamic outline with HTTP, configure, callonce, Java interop

  # Tests combined:
  # - Dynamic outline with @setup
  # - configure headers from callonce
  # - configure cookies set here
  # - configure afterScenario hook
  # - Java interop from callonce and callSingle
  # - HTTP calls with headers/cookies
  # - karate-base.js functions
  # - call to another feature within outline

  Background:
    * url serverUrl
    * def HelloBg = HelloConfigSingle
    * callonce read('http-callonce.feature')
    * configure cookies = read('http-cookies.js')
    * configure afterScenario =
      """
      function fn() {
        karate.log('afterScenario executed');
      }
      """

  @setup
  Scenario:
    * def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' } ]

  Scenario Outline: dynamic outline with HTTP - <name>
    * call read('http-called.feature')
    * match baseFunction('outline') == 'base:outline'
    * path 'echo', '<name>'
    * method get
    * status 200
    * match response.id == '<name>'
    * match response.testId == '#string'
    # Verify Java interop
    * match HelloBg.sayHello('world') == 'hello world'
    * match HelloOnce.sayHello('world') == 'hello world'
    * match sayHello('world') == 'hello world'

    Examples:
      | karate.setup().data |

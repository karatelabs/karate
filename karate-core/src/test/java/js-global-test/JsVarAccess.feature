Feature: Demonstrate JS global var access failure after calling feature in nested context (via Java)

  Background:
    * def Runner = Java.type('com.intuit.karate.Runner')
    * call read('classpath:js-global-test/Nothing.feature')

  @JsScenarioWithoutCallToNestedRunner
  Scenario: Accessing a global var from a JavaScript function succeeds without nested feature call
    * def aGlobalVar1 = 'some value 1'
    * def jsFunc1 =
    """
    function() {
      print("aGlobalVar1 (from JavaScript): " + aGlobalVar1)
      return aGlobalVar1
    }
    """
    * print "aGlobalVar1 (from Karate): " + aGlobalVar1
    * def jsFuncResult1 = jsFunc1()
    * match jsFuncResult1 == 'some value 1'

  @JsScenarioWithCallToNestedRunner
  Scenario: Accessing a global var from a JavaScript function fails after nested feature call
    * def aGlobalVar2 = 'some value 2'
    * def jsFunc2 =
    """
    function() {
      print("aGlobalVar2 (from JavaScript): " + aGlobalVar2)
      return aGlobalVar2
    }
    """
    * Runner.runFeature('classpath:js-global-test/Nothing.feature', null, false)  // test succeeds if this line is commented out
    * print "aGlobalVar2 (from Karate): " + aGlobalVar2
    * def jsFuncResult2 = jsFunc2()
    * match jsFuncResult2 == 'some value 2'
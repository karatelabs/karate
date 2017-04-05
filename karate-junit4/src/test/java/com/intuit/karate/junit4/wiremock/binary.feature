@ignore
Feature: testing binary response handling

Scenario: get binary result and make sure it hasn't been corrupted
  * def checkBinaryResult =
  """
  function(b){
    var Runner = Java.type('com.intuit.karate.junit4.wiremock.HelloWorldTest')
    var expected =  Runner.testBytes;
    var IOUtils = Java.type('org.apache.commons.io.IOUtils');
    var actualBytes = IOUtils.toByteArray(b);
    print('expected byte count: ' + expected.length + ', response byte count: ' + actualBytes.length);
    return(expected.length == actualBytes.length)
  }
  """
Given url 'http://localhost:' + wiremockPort + '/v1/binary/'
When method get
Then status 200
Then assert checkBinaryResult(response)

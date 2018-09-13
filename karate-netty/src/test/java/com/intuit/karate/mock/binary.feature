Feature: testing binary response handling

Background: 
    * def Runner = Java.type('com.intuit.karate.mock.MockServerTest')

Scenario: get binary result and make sure it hasn't been corrupted
    * def checkBinaryResult =
    """
    function(actual){
      var expected = Runner.testBytes;
      print('expected byte count: ' + expected.length + ', response byte count: ' + actual.length);
      return java.util.Arrays.equals(expected, actual);
    }
    """
    Given url mockServerUrl
    And path 'binary', 'download'
    When method get
    Then status 200
    Then assert checkBinaryResult(responseBytes)

Scenario: send binary content and make sure the server can see it
    Given url mockServerUrl
    And path 'binary', 'upload'
    * def bytes = Runner.testBytes
    And request Runner.testBytes
    When method post
    Then status 200
    And match response == { success: true }

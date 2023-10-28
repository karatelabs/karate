Feature: responseStatus variable tests
  Scenario: config js test
    Given url 'https://www.baidu.com'
    When method GET
    Then status 200

    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200_config()

  Scenario: call test
    Given url 'https://www.baidu.com'
    When method GET
    Then status 200

    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200_call()
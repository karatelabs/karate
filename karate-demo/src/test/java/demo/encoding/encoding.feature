Feature: encoding tests

Scenario: special characters in the url
    Given url demoBaseUrl + '/encoding/�Ill~Formed@RequiredString!'
    When method get
    Then status 200
    And match response == '�Ill~Formed@RequiredString!'

Scenario: special characters in the path
    Given url demoBaseUrl
    And path 'encoding', '�Ill~Formed@RequiredString!'
    When method get
    Then status 200
    And match response == '�Ill~Formed@RequiredString!'

Scenario: question mark in the url
    Given url demoBaseUrl + '/encoding/index.php%3F/api/v2'
    And path 'hello'
    When method get
    Then status 200
    And match response == 'hello'
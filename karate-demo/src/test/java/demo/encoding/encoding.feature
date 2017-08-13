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


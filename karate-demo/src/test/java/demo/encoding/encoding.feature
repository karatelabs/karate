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

Scenario: manually decode before passing to karate
    * def encoded = 'encoding%2Ffoo%2Bbar'
    * def decoded = java.net.URLDecoder.decode(encoded, 'UTF-8')
    Given url demoBaseUrl
    And path decoded
    When method get
    Then status 200
    And match response == 'foo+bar'

Feature: show how a headers js routine can pass variables to the calling
    or currently executing feature

Scenario: check that the header sent was as expected
    * configure headers = read('headers-alt.js')
    Given url demoBaseUrl
    And path 'search', 'headers'
    When method get
    Then status 200
    And match prevUuid == response.token[0]
  

Feature:

  Scenario: post redirect to get
    * def port = karate.start('parse-html.feature').port
    * def loginUrl = 'http://localhost:' + port + '/login'

    Given url loginUrl
    And request {}
    When method POST
    Then status 200
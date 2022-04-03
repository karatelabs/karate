Feature:

Scenario: cats crud
    Given url mockServerUrl + '/cats'

    Given path 0
    When method get
    Then status 404

    Given request { name: 'Billie' }
    When method post
    Then status 200
    And match response == { id: '#number', name: 'Billie' }
    * def billie = response

    Given path billie.id
    When method get
    Then status 200
    And match response == billie

    Given request { name: 'Wild' }
    When method post
    Then status 200
    And match response == { id: '#number', name: 'Wild' }
    * def wild = response

    Given path wild.id
    When method get
    Then status 200
    And match response == wild

    When method get
    Then status 200
    And match response contains ([billie, wild])
    # And match header Access-Control-Allow-Origin == '*'

Scenario: body json path expression
    Given url mockServerUrl + '/body/json'
    And request { name: 'Scooby' }
    When method post
    Then match response == { success: true }
    
Scenario: body xml path expression
    Given url mockServerUrl + '/body/xml'
    And request <dog><name>Scooby</name></dog>
    When method post
    Then match response == { success: true }

Scenario: karate.abort() test
    Given url mockServerUrl + '/abort'
    When method get
    Then match response == { success: true }
    * karate.abort()
    * match 1 == 2

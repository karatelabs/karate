Feature:

Scenario: cats crud
    * url mockServerUrl + 'cats'

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
    And match header Access-Control-Allow-Origin == '*'

Scenario: cors options method handling
    Given url mockServerUrl
    When method options
    Then status 200
    And match header Allow == 'GET, HEAD, POST, PUT, DELETE, PATCH'
    And match header Access-Control-Allow-Origin == '*'    
    And match header Access-Control-Allow-Methods == 'GET, HEAD, POST, PUT, DELETE, PATCH'    
    And match response == ''

Scenario: cors options with access-control-request-headers
    Given url mockServerUrl
    And header Access-Control-Request-Headers = 'POST'
    When method options   
    Then status 200
    And match header Allow == 'GET, HEAD, POST, PUT, DELETE, PATCH'
    And match header Access-Control-Allow-Origin == '*'    
    And match header Access-Control-Allow-Methods == 'GET, HEAD, POST, PUT, DELETE, PATCH'
    And match header Access-Control-Allow-Headers == 'POST'
    And match response == ''

Scenario: body json path expression
    Given url mockServerUrl + 'body/json'
    And request { name: 'Scooby' }
    When method post
    Then match response == { success: true }
    
Scenario: body xml path expression
    Given url mockServerUrl + 'body/xml'
    And request <dog><name>Scooby</name></dog>
    When method post
    Then match response == { success: true }

Scenario: karate.abort() test
    Given url mockServerUrl + 'abort'
    When method get
    Then match response == { success: true }
    * karate.abort()
    * match 1 == 2

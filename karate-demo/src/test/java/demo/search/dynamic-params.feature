Feature: dynamic params using scenario-outline, examples and json

Background:
    * url demoBaseUrl

Scenario Outline: using a function to pre-process the search parameters
    * def query = { name: '<name>', country: '<country>', active: '<active>', limit: '<limit>' }
    * def nullify = 
    """
    function(o) {
      for (var key in o) {
        if (o[key] == '') o[key] = null;
      }
      return o;
    }
    """
    * def getResponseParam = read('get-response-param.js')
    * def query = nullify(query)
    * print query

    Given path 'search'
    # the 'params' keyword takes json, and will ignore any key that has a null value
    And params query
    When method get
    Then status 200

    And assert getResponseParam('name') == query.name
    And assert getResponseParam('country') == query.country
    And assert getResponseParam('active') == query.active
    And assert getResponseParam('limit') == query.limit

    # response should NOT contain a key expected to be missing
    And match response !contains { '<missing>': '#notnull' }

    Examples:
    | name | country | active | limit | missing |
    | foo  | IN      | true   |     1 |         |
    | bar  |         |        |     5 | country |
    | bar  | JP      |        |       | active  |
    |      | US      |        |     3 | name    |
    |      |         | false  |       | limit   |

Scenario Outline: here the parameters are set to null in the data table itself
    # look at how this is different from the above, the quotes come from the Examples
    * def query = { name: <name>, country: <country>, active: <active>, limit: <limit> }
    * print query

    Given path 'search'
    And params query
    When method get
    Then status 200
    And match response !contains { <missing>: '#notnull' }

    # note how strings are enclosed in quotes, and we set null-s here
    Examples:
    | name   | country   | active | limit | missing |
    | 'foo'  | 'IN'      | true   |     1 | dummy   |
    | 'bar'  | null      | null   |     5 | country |
    | 'bar'  | 'JP'      | null   |  null | active  |
    | null   | 'US'      | null   |     3 | name    |
    | null   | null      | false  |  null | limit   |


Scenario: using a data-driven called feature instead of a scenario outline
    * table data
    | name   | country   | active | limit | missing                      |
    | 'foo'  | 'IN'      | true   |     1 | []                           |
    | 'bar'  |           |        |     5 | ['country', 'active']        |
    | 'bar'  | 'JP'      |        |       | ['active', 'limit']          |
    |        | 'US'      |        |     3 | ['name', 'active']           |
    |        |           | false  |       | ['name', 'country', 'limit'] |
    
    * def result = call read('search-complex.feature') data    

Scenario: params json with embedded expressions
    * def data = { one: 'one', two: 'two' }

    Given path 'search'
    And params { name: '#(data.one)', country: '#(data.two)' }
    When method get
    Then status 200
    And match response == { name: ['one'], country: ['two'] }

Scenario: using the set keyword to build json and nulls are skipped by default
    this is possibly the simplest form of all the above, avoiding any javascript

    * set data
    | path    | 0       | 1       | 2       | 3       | 4       |
    | name    | 'foo'   | 'bar'   | 'bar'   |         |         |
    | country | 'IN'    |         | 'JP'    | 'US'    |         |
    | active  | true    |         |         |         | false   |
    | limit   | 1       | 5       |         | 3       |         |
    
    * table search
    | params  | missing                                                      |
    | data[0] | {}                                                           |
    | data[1] | { country: '#notnull', active: '#notnull' }                  |
    | data[2] | { active: '#notnull', limit: '#notnull' }                    |
    | data[3] | { name: '#notnull', active: '#notnull' }                     |
    | data[4] | { name: '#notnull', country: '#notnull', limit: '#notnull' } |

    * def result = call read('search-simple.feature') search

Scenario: test that multi-params work as expected
    
    Given path 'search'
    And param foo = 'bar', 'baz'
    When method get
    Then status 200
    And match response == { foo: ['bar', 'baz'] }

    Given path 'search'
    And params { foo: ['bar', 'baz'] }
    When method get
    Then status 200
    And match response == { foo: ['bar', 'baz'] }

    Given path 'search'
    And param foo = 'bar,baz'
    When method get
    Then status 200
    And match response == { foo: ['bar,baz'] }

    Given path 'search'
    And params { foo: 'bar,baz' }
    When method get
    Then status 200
    And match response == { foo: ['bar,baz'] }
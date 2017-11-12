Feature: dynamic params using scenario-outline, examples and json

Background:
    * url demoBaseUrl

Scenario Outline: using a javascript function to pre-process the search parameters
    this particular example has been deliberately over-complicated, the next scenario-outline below is simpler

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
    # here we load a java-script function from a re-usable file
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
    # notice how this is different from the above, the quotes come from the 'Examples' section
    * def query = { name: <name>, country: <country>, active: <active>, limit: <limit> }
    * print query

    Given path 'search'
    And params query
    When method get
    Then status 200
    # response should NOT contain a key expected to be missing
    And match response !contains <missing>

    # observe how strings are enclosed in quotes, and we set null-s here below
    # and you can get creative by stuffing json into table cells !
    Examples:
    | name   | country   | active | limit | missing                                                      |
    | 'foo'  | 'IN'      | true   |     1 | {}                                                           |
    | 'bar'  | null      | null   |     5 | { country: '#notnull', active: '#notnull' }                  |
    | 'bar'  | 'JP'      | null   |  null | { active: '#notnull', limit: '#notnull' }                    |
    | null   | 'US'      | null   |     3 | { name: '#notnull', active: '#notnull' }                     |
    | null   | null      | false  |  null | { name: '#notnull', country: '#notnull', limit: '#notnull' } |

Scenario: using a data-driven called feature instead of a scenario outline
    this and the above example are the two fundamental ways of 'looping' in Karate

    * table data
    | name   | country   | active | limit | missing                      |
    | 'foo'  | 'IN'      | true   |     1 | []                           |
    | 'bar'  |           |        |     5 | ['country', 'active']        |
    | 'bar'  | 'JP'      |        |       | ['active', 'limit']          |
    |        | 'US'      |        |     3 | ['name', 'active']           |
    |        |           | false  |       | ['name', 'country', 'limit'] |
    
    # the assertions in the called feature use some js for the sake of demo
    # but the next scenario below is far simpler and does not use js at all
    * def result = call read('search-complex.feature') data

Scenario: using the set keyword to build json and nulls are skipped by default
    this is possibly the simplest form of all the above, avoiding any javascript
    but still needing a 'call' to a second feature file

    # table would have been sufficient below, but just to demo how 'set' is simply a 'transpose' of table
    * set data
    | path    | 0       | 1       | 2       | 3       | 4       |
    | name    | 'foo'   | 'bar'   | 'bar'   |         |         |
    | country | 'IN'    |         | 'JP'    | 'US'    |         |
    | active  | true    |         |         |         | false   |
    | limit   | 1       | 5       |         | 3       |         |
    
    # note how you can 'compose' complex JSON by referring to existing JSON chunks, e.g: 'data[0]'
    * table search
    | params  | expected                                                         | missing                                                      |
    | data[0] | { name: '#[1]', country: '#[1]', active: '#[1]', limit: '#[1]' } | {}                                                           |
    | data[1] | { name: '#[1]', limit: '#[1]' }                                  | { country: '#notnull', active: '#notnull' }                  |
    | data[2] | { name: '#[1]', country: '#[1]' }                                | { active: '#notnull', limit: '#notnull' }                    |
    | data[3] | { country: '#[1]', limit: '#[1]' }                               | { name: '#notnull', active: '#notnull' }                     |
    | data[4] | { active: '#[1]' }                                               | { name: '#notnull', country: '#notnull', limit: '#notnull' } |

    * def result = call read('search-simple.feature') search

Scenario: params json with embedded expressions
    * def data = { one: 'one', two: 'two' }

    Given path 'search'
    # using enclosed javascript instead of an embedded expression for convenience
    And params ({ name: data.one, country: data.two })
    When method get
    Then status 200
    And match response == { name: ['one'], country: ['two'] }

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

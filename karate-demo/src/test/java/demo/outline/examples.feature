Feature: patterns for using cucumber scenario-outline and examples with karate

Background:
    * url demoBaseUrl

Scenario Outline: name: <name> and country: <country>
    avoid empty cells and use null in 'Examples' to work better with karate (or use type hints, see below)
        and also consider stuffing whole chunks of json into cells

    Given path 'search'
    And params { name: <name>, country: <country>, active: <active>, limit: <limit> }
    When method get
    Then status 200
    And match response == <expected>

    Examples:
        | name   | country   | active | limit | expected                                                                         |
        | 'foo'  | 'IN'      | true   |     1 | { name: '#notnull', country: '#notnull', active: '#notnull', limit: '#notnull' } |
        | 'bar'  | null      | null   |     5 | { name: '#notnull', limit: '#notnull' }                                          |
        | 'baz'  | 'JP'      | null   |  null | { name: '#notnull', country: '#notnull' }                                        |
        | null   | 'US'      | null   |     3 | { country: '#notnull', limit: '#notnull' }                                       |
        | null   | null      | false  |  null | { active: '#notnull' }                                                           |

Scenario Outline: name: <name> and country: <country>
    above example simplified / improved using type-hints and karate's enhancements to example row handling

    Given path 'search'
    # since the next line is standard JSON + embedded-expressions, it can be easily extracted into a re-usable file
    And params { name: '#(name)', country: '#(country)', active: '#(active)', limit: '#(limit)' }
    When method get
    Then status 200
    And match response == expected

    Examples:
        | name | country | active! | limit! | expected!                                                                        |
        | foo  | IN      | true    |      1 | { name: '#notnull', country: '#notnull', active: '#notnull', limit: '#notnull' } |
        | bar  |         |         |      5 | { name: '#notnull', limit: '#notnull' }                                          |
        | baz  | JP      |         |        | { name: '#notnull', country: '#notnull' }                                        |
        |      | US      |         |      3 | { country: '#notnull', limit: '#notnull' }                                       |
        |      |         | false   |        | { active: '#notnull' }                                                           |

Scenario Outline: expressions - index: <index> and country: <country>
    combine 'Examples' embedded expressions and karate expression evaluation

    * def names = { first: 'foo', second: 'bar', third: 'baz', fourth: null, fifth: null }
    * def expected =
    """
    [
        { name: '#notnull', country: '#notnull', active: '#notnull', limit: '#notnull' },
        { name: '#notnull', limit: '#notnull' },
        { name: '#notnull', country: '#notnull' },
        { country: '#notnull', limit: '#notnull' },
        { active: '#notnull' }
    ]
    """    

    Given path 'search'
    # note how the Examples column for 'name' works here
    And params { name: '#(<name>)', country: <country>, active: <active>, limit: <limit> }
    When method get
    Then status 200
    And match response == expected[<index>]

    Examples:
        | name         | country   | active | limit | index |
        | names.first  | 'IN'      | true   |     1 | 0     |
        | names.second | null      | null   |     5 | 1     |
        | names.third  | 'JP'      | null   |  null | 2     |
        | names.fourth | 'US'      | null   |     3 | 3     |
        | names.fifth  | null      | false  |  null | 4     |
        
Scenario Outline: expressions - index: <index> and country: <country>
    the above outline re-written to use karate's enhanced row-handling

    * def names = { first: 'foo', second: 'bar', third: 'baz', fourth: null, fifth: null }
    * def expected =
    """
    [
        { name: '#notnull', country: '#notnull', active: '#notnull', limit: '#notnull' },
        { name: '#notnull', limit: '#notnull' },
        { name: '#notnull', country: '#notnull' },
        { country: '#notnull', limit: '#notnull' },
        { country: '#notnull', active: '#notnull' }
    ]
    """  

    Given path 'search'
    # both mechanisms for data substitution are available at the same time
    And params { name: '#(<name>)', country: '#(country)', active: '#(active)', limit: '#(limit)' }
    When method get
    Then status 200
    # note how the row index is a magic variable
    And match response == expected[__num]

    # if you really wanted an empty or blank string as row-data, just use type-hints combined with quoted strings
    Examples:
        | name         | country! | active! | limit! |
        | names.first  | 'IN'     | true    |      1 |
        | names.second |          |         |      5 |
        | names.third  | 'JP'     |         |        |
        | names.fourth | 'US'     |         |      3 |
        | names.fifth  | ''       | false   |        |
 
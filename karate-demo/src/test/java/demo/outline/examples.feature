Feature: patterns for using cucumber scenario-outline and examples with karate

Background:
    * url demoBaseUrl

Scenario Outline: avoid empty cells and use null in 'Examples' to work better with karate
    and also consider stuffing whole chunks of json into cells

    Given path 'search'
    And params { name: <name>, country: <country>, active: <active>, limit: <limit> }
    When method get
    Then status 200
    # response should NOT contain a key expected to be missing
    And match response !contains <missing>

    Examples:
        | name   | country   | active | limit | missing                                                      |
        | 'foo'  | 'IN'      | true   |     1 | {}                                                           |
        | 'bar'  | null      | null   |     5 | { country: '#notnull', active: '#notnull' }                  |
        | 'baz'  | 'JP'      | null   |  null | { active: '#notnull', limit: '#notnull' }                    |
        | null   | 'US'      | null   |     3 | { name: '#notnull', active: '#notnull' }                     |
        | null   | null      | false  |  null | { name: '#notnull', country: '#notnull', limit: '#notnull' } |

Scenario Outline: combine 'Examples' embedded expressions and karate expression evaluation

    * def names = { first: 'foo', second: 'bar', third: 'baz', fourth: null, fifth: null }
    * def missing =
    """
    [
        {},
        { country: '#notnull', active: '#notnull' },
        { active: '#notnull', limit: '#notnull' },
        { name: '#notnull', active: '#notnull' },
        { name: '#notnull', country: '#notnull', limit: '#notnull' }
    ]
    """    

    Given path 'search'
    # note how the Examples column for 'name' works here
    And params { name: '#(<name>)', country: <country>, active: <active>, limit: <limit> }
    When method get
    Then status 200
    And match response !contains missing[<index>]

    Examples:
        | name         | country   | active | limit | index |
        | names.first  | 'IN'      | true   |     1 | 0     |
        | names.second | null      | null   |     5 | 1     |
        | names.third  | 'JP'      | null   |  null | 2     |
        | names.fourth | 'US'      | null   |     3 | 3     |
        | names.fifth  | null      | false  |  null | 4     |
        
Feature: test graphql end point

Background:
* url demoBaseUrl + '/graphql'
# this live url should work if you want to try this on your own
# * url 'https://graphql-pokemon.now.sh'

Scenario: simple graphql request
    # note the use of text instead of def since this is NOT json    
    Given text query =
    """
    {
      pokemon(name: "Pikachu") {
        id
        number
        name
        attacks {
          special {
            name
            type
            damage
          }
        }
      }
    }
    """
    And request { query: '#(query)' }
    When method post
    Then status 200

    # pretty print the response
    * print 'response:', response

    # json-path makes it easy to focus only on the parts you are interested in
    # which is especially useful for graph-ql as responses tend to be heavily nested
    # '$' happens to be a JsonPath-friendly short-cut for the 'response' variable
    * match $.data.pokemon.number == '025'

    # the '..' wildcard is useful for traversing deeply nested parts of the json
    * def attacks = get[0] response..special
    * match attacks contains { name: 'Thunderbolt', type: 'Electric', damage: 55 }

Scenario: graphql from a file and variables
    # here the query is read from a file
    # note that the 'replace' keyword (not used here) can also be very useful for dynamic query building
    Given def query = read('by-name.graphql')
    And def variables = { name: 'Charmander' }
    And request { query: '#(query)', variables: '#(variables)' }
    When method post
    Then status 200
    
    * def expected = [{ name: 'Flamethrower', type: 'Fire', damage: 55 }, { name: 'Flame Charge', type: 'Fire', damage: 25 }]
    # this one liner does quite a lot ! note how the order of elements in the above array does not matter
    * match $.data.pokemon contains { number: '004', name: 'Charmander', attacks: { special: '#(^expected)' } }

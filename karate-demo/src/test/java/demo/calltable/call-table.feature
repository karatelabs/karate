Feature: calling another feature file in a loop

Background:
* url demoBaseUrl

* table kittens
    | name       | age |
    | 'Bob'      | 2   |
    | 'Wild'     | 1   |
    | 'Nyan'     | 3   |
    | 'Keyboard' | 5   |
    | 'LOL'      | 3   |
    | 'Ceiling'  | 2   |

* def result = call read('kitten-create.feature') kittens

# use json-path to 'un-pack' the array of kittens created
* def created = $result[*].response

# for each iteration, a variable called '__loop' is set for convenience
# which can be accessed in the called feature as well
* match result[*].__loop == [0, 1, 2, 3, 4, 5]

# which is not even needed for most data-driven assertions
* match created[*].name == $kittens[*].name

Scenario: create parent cat using kittens

# create mom cat
Given path 'cats'
And request { name: 'Billie', kittens: '#(created)' }
When method post
Then status 200
# the '^^' is an embeddable short-cut for 'contains only' !
And match response == { id: '#number', name: 'Billie', kittens: '#(^^created)' }

# get kittens for billie using the id from the previous response
Given path 'cats', $.id, 'kittens'
When method get
Then status 200

# some demo match examples
* match each response == { id: '#number', name: '#string' }
* match response == "#[6] { id: '#number', name: '#string' }"

# pure data-driven assertion, compare with the original data
* match response[*].name contains only $kittens[*].name

* assert response.length == 6
# prefer match instead of assert
* match response == '#[6]'

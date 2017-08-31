Feature: calling another feature file in a loop

Background:
* url demoBaseUrl

* table kittens = 
    | name       | age |
    | 'Bob'      | 2   |
    | 'Wild'     | 1   |
    | 'Nyan'     | 3   |
    | 'Keyboard' | 5   |
    | 'LOL'      | 3   |
    | 'Ceiling'  | 2   |

* def result = call read('kitten-create.feature') kittens

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
And def billie = response

# get kittens for billie
Given path 'cats', billie.id, 'kittens'
When method get
Then status 200
And match each response == { id: '#number', name: '#string' }
And match response[*].name contains ['LOL', 'Nyan']
And assert response.length == 6




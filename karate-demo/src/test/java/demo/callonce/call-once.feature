Feature: how to implement 'run only once' or set-up that behaves like @BeforeClass

Background:

* url demoBaseUrl

* table kittens = 
    | name     | age |
    | Bob      | 2   |
    | Wild     | 1   |
    | Nyan     | 3   |

* def result = call read('create-kittens-once.js') kittens

Scenario Outline: various tests on the cats created

    Given path 'cats'
    When method get
    Then status 200
    And match response[*].name contains '<name>'

    # even though cucumber executes the 'Background' for each row in the 'Examples',
    # 'create-kittens-once.js' is designed to cache expensive calls

    Examples:
    | name |
    | Bob  |
    | Nyan |


# again, even though cucumber calls the 'Background' for each Scenario,
# 'create-kittens-once.js' will make http calls only once
Scenario: create a cat with kittens

    * def created = get result[*].response

    Given path 'cats'
    And request { name: 'Billie', kittens: '#(created)' }
    When method post
    Then status 200
    And match response.kittens[*].name contains only ['Bob', 'Wild', 'Nyan']





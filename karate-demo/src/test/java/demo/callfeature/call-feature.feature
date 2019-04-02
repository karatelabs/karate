Feature: calling another feature file

Background:
* url demoBaseUrl

Scenario: calling a feature with parameters
    # the second parameter age is just for demo, it is ignored in 'create-cat.feature'
    * def result = call read('called-normal.feature') { name: 'Nyan', age: 10 }
    # we need to 'unpack' variables out of the call result
    * def nyan = result.response
    * match nyan == { id: '#number', name: 'Nyan' }

Scenario: called feature uses '__arg', you can use variable references as the call argument
    * def nyan = { name: 'Nyan' }
    * def result = call read('called-arg.feature') nyan
    * match result.response contains nyan

Scenario: called features will inherit parent scope
    # this variable will be available in the called feature
    * def name = 'Nyan'
    # and we can call without an argument
    * def result = call read('called-normal.feature')
    * match result.response contains { name: '#(name)' }

Scenario: calling with shared scope, recommended only for 'set-up' re-usable routines
    # this variable will be available in the called feature
    * def name = 'Nyan'
    # this will update variables 'globally'
    * call read('called-normal.feature')
    * match response contains { name: '#(name)' }

Scenario: create kittens and then create parent cat
    * def kittens = call read('create-two-cats.feature')
    * def bob = kittens.bob
    * def wild = kittens.wild

    # create mom cat
    Given path 'cats'
    # sometimes, enclosed javascript is more convenient than embedded expressions
    And request ({ name: 'Billie', kittens: [bob, wild] })
    When method post
    Then status 200
    And match response == read('../cats/billie-expected.json')
    And def billie = response

    # get kittens for billie
    Given path 'cats', billie.id, 'kittens'
    When method get
    Then status 200
    And match each response == { id: '#number', name: '#string' }
    And match response contains { id: '#(wild.id)', name: 'Wild' }

Scenario: create kittens but calling a feature that has a scenario outline (not recommended)
    but interesting example of a called feature updating a 'global' variable
    
    # init 'global' variable
    * def kittens = []
    * def result = call read('create-cats-outline.feature')   
 
    Given path 'cats'
    And request { name: 'Billie', kittens: '#(kittens)' }
    When method post
    Then status 200
    And match response == { id: '#number', name: 'Billie', kittens: '#(^^kittens)' }
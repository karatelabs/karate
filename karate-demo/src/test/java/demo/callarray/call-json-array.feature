Feature: calling another feature file in a loop

Background:
    * url demoBaseUrl

    * def creator = read('kitten-create.feature')
    * def kittens = read('kittens.json')
    * def result = call creator kittens

    # the above could be written in one line as follows
    # * def result = call read('kitten-create.feature') read('kittens.json')

    * def created = $result[*].response

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




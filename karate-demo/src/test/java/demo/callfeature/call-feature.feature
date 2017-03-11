Feature: calling another feature file

Background:
* url demoBaseUrl

Scenario: create kittens and then create parent cat

* def kittens = call read('create-two-cats.feature')
* def bob = kittens.bob
* def wild = kittens.wild

# create mom cat
Given path 'cats'
And request { name: 'Billie', kittens: ['#(bob)', '#(wild)'] }
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




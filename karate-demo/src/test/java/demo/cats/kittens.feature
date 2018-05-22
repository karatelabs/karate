Feature: cats with kittens

Background:
* url demoBaseUrl

Scenario: create cat with kittens

# create bob cat
Given path 'cats'
And request { name: 'Bob' }
When method post
Then status 200
And def bob = response

# create wild cat
Given path 'cats'
And request { name: 'Wild' }    
When method post
Then status 200
And def wild = response

# create mom cat
Given path 'cats'
# sometimes, enclosed javascript is more convenient than embedded expressions
And request ({ name: 'Billie', kittens: [bob, wild] })
When method post
Then status 200
And match response == read('billie-expected.json')
And def billie = response

# get kittens for billie
Given path 'cats', billie.id, 'kittens'
When method get
Then status 200
And match each response == { id: '#number', name: '#string' }
And match response contains { id: '#(wild.id)', name: 'Wild' }

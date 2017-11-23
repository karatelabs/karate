Feature: dogs end-point that uses jdbc as part of the test

Background:
* url demoBaseUrl

Scenario: create and retrieve a dog

# create a dog
Given path 'dogs'
And request { name: 'Scooby' }
When method post
Then status 200
And match response == { id: '#number', name: 'Scooby' }

* def id = response.id

# get by id
Given path 'dogs', id
When method get
Then status 200
And match response == { id: '#(id)', name: 'Scooby' }

# get all dogs
Given path 'dogs'
When method get
Then status 200
And match response contains { id: '#(id)', name: 'Scooby' }

# use jdbc to validate
* def config = { username: 'sa', password: '', url: 'jdbc:h2:mem:testdb', driverClassName: 'org.h2.Driver' }
* def DbUtils = Java.type('com.intuit.karate.demo.util.DbUtils')
* def db = new DbUtils(config)

# since the DbUtils returns a Java Map, it becomes normal JSON here !
# which means that you can use the full power of Karate's 'match' syntax
* def dogs = db.readRows('SELECT * FROM DOGS')
* match dogs contains { ID: '#(id)', NAME: 'Scooby' }

* def dog = db.readRow('SELECT * FROM DOGS D WHERE D.ID = ' + id)
* match dog.NAME == 'Scooby'

* def test = db.readValue('SELECT ID FROM DOGS D WHERE D.ID = ' + id)
* match test == id

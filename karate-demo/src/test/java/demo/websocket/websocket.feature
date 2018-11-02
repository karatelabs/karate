@mock-servlet-todo
Feature: websocket testing

Scenario: only listening to websocket messages
    * def handler = function(msg){ if (msg.startsWith('{')) karate.signal(msg) }
    * eval karate.webSocket(demoBaseUrl + '/websocket', handler)

    # first we post to the /websocket-controller end-point which will broadcast a message
    # to any websocket clients that are connected - but after a delay of 1 second    
    Given url demoBaseUrl
    And path 'websocket-controller'
    And request { text: 'Rudy' }
    When method post
    Then status 200
    And def id = response.id

    # this line will wait until karate.signal() has been called
    * def result = karate.listen(5000)
    * match result == { id: '#(id)', content: 'hello Rudy !' }

Scenario: using the websocket instance to send as well as receive messages
    * def handler = function(msg){ if (msg.startsWith('hello')) karate.signal(msg) }
    * def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)
    * eval socket.send('Billie')
    * def result = karate.listen(5000)
    * match result == 'hello Billie !'

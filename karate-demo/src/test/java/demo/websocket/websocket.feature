@mock-servlet-todo
@ignore
Feature: websocket testing

Scenario: only listening to websocket messages
    * def handler = function(msg){ return msg.startsWith('{') }
    * def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)

    # first we post to the /websocket-controller end-point which will broadcast a message
    # to any websocket clients that are connected - but after a delay of 1 second    
    Given url demoBaseUrl
    And path 'websocket-controller'
    And request { text: 'Rudy' }
    When method post
    Then status 200
    And def id = response.id

    # this line will wait until the handler returns true
    * listen 5000
    * json result = listenResult
    * match result == { id: '#(id)', content: 'hello Rudy !' }

Scenario: using the websocket instance to send as well as receive messages
    * def handler = function(msg){ return msg.startsWith('hello') }
    * def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)
    * socket.send('Billie')
    * listen 5000
    * match listenResult == 'hello Billie !'

Scenario: listen for multiple websocket messages
    * def handler = function(msg){ return msg.startsWith('hello') }
    * def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)
    * socket.send('Billie')
    * listen 5000
    * match listenResult == 'hello Billie !'
    * socket.send('Bob')
    * listen 5000
    * match listenResult == 'hello Bob !'

Scenario: change the websocket handler for messages
    * def handler = function(msg){ return msg.contains('Billie') }
    * def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)
    * socket.send('Billie')
    * listen 5000
    * match listenResult == 'hello Billie !'
    * def handler = function(msg){ return msg.contains('Bob') }
    * socket.setTextHandler(handler)
    * socket.send('Bob')
    * listen 5000
    * match listenResult == 'hello Bob !'
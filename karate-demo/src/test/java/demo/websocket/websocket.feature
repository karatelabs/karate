@mock-servlet-todo
Feature: websocket testing

Scenario: only listening to websocket messages
    * def ws = karate.webSocket(demoBaseUrl + '/websocket', handler)
    * eval ws.setTextFilter(function(msg){ return msg.startsWith('{') })

    # first we post to the /websocket-controller end-point which will broadcast a message
    # to any websocket clients that are connected - but after a delay of 1 second    
    Given url demoBaseUrl
    And path 'websocket-controller'
    And request { text: 'Rudy' }
    When method post
    Then status 200
    And def id = response.id

    # this line will wait until karate.signal() has been called
    * def result = ws.listen(5000)
    * match result == { id: '#(id)', content: 'hello Rudy !' }

Scenario: using the websocket instance to send as well as receive messages
    * def ws = karate.webSocket(demoBaseUrl + '/websocket')
    * eval ws.setTextFilter(function(msg){ return msg.startsWith('hello') })
    * eval ws.send('Billie')
    * def result = ws.listen(5000)
    * match result == 'hello Billie !'

Scenario: perform custom action when message is received
    * def ws = karate.webSocket(demoBaseUrl + '/websocket')
    # will call the function myJavaScriptFunction (which is defined elsewhere) for each text message that is received
    * eval ws.setTextHandler(function(msg){ myJavaScriptFunction() })
    * eval ws.send('Billie')
    * def result = ws.listen(5000)
    * match result == 'hello Billie !'

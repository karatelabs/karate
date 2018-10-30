@mock-servlet-todo
Feature: websocket testing

Background:
    * def received = null

Scenario: only listening to websocket messages
    * def handler = function(msg){ if (!msg.startsWith('{')) return; karate.set('received', msg); karate.signal() }
    * def socket = karate.websocket(demoBaseUrl + '/websocket', handler)

    # first we post to the /dogs end-point
    # which will broadcast a message to any websocket clients that have connected
    # after a delay of 1 second
    Given url demoBaseUrl
    And path 'websocket-controller'
    And request { text: 'Rudy' }
    When method post
    Then status 200
    And def id = response.id

    # this line will wait until karate.signal() has been called
    * eval karate.listen(5000)
    * match received == { id: '#(id)', content: 'hello Rudy !' }

Scenario: using the websocket instance to send as well as receive messages
    * def handler = function(msg){ if (!msg.startsWith('hello')) return; karate.set('received', msg); karate.signal() }
    * def socket = karate.websocket(demoBaseUrl + '/websocket', handler)
    * eval socket.send('Billie')
    * eval karate.listen(5000)
    * match received == 'hello Billie !'

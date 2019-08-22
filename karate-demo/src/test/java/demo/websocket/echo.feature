@ignore
Feature: public test at 
    http://www.websocket.org/echo.html

Scenario: text messages
    And def socket = karate.webSocket('ws://echo.websocket.org')
    When socket.send('hello world!')
    And def result = socket.listen(5000)
    Then match result == 'hello world!'

    When socket.send('another test')
    And def result = socket.listen(5000)
    Then match result == 'another test'

Scenario: binary message
    And def socket = karate.webSocketBinary('ws://echo.websocket.org')
    And bytes data = read('../upload/test.pdf')
    When socket.sendBytes(data)
    And def result = socket.listen(5000)
    # the result data-type is byte-array, but this comparison works
    Then match result == read('../upload/test.pdf')

Scenario: sub protocol
    Given def demoBaseUrl = 'wss://subscriptions.graph.cool/v1/cizfapt9y2jca01393hzx96w9'
    And def options = { subProtocol: 'graphql-subscriptions', headers: { Authorization: 'Bearer foo' } }
    And def socket = karate.webSocket(demoBaseUrl, null, options)
    And def txt = '{"type": "connection_init", "payload": {}}'
    When socket.send(txt)
    And def result = socket.listen(5000)
    Then match result == { type: 'connection_ack' }
@ignore
Feature: public test at 
    http://www.websocket.org/echo.html

Scenario: text messages
    Given def handler = function(msg){ karate.signal(msg) }
    And def socket = karate.webSocket('ws://echo.websocket.org', handler)
    When eval socket.send('hello world!')
    And def result = karate.listen(5000)
    Then match result == 'hello world!'

    When eval socket.send('another test')
    And def result = karate.listen(5000)
    Then match result == 'another test'

Scenario: binary message
    Given def handler = function(msg){ karate.signal(msg) }
    And def socket = karate.webSocket('ws://echo.websocket.org', null, null, handler)
    And bytes data = read('../upload/test.pdf')
    When eval socket.sendBytes(data)
    And def result = karate.listen(5000)
    # the result data-type is byte-array, but this comparison works
    Then match result == read('../upload/test.pdf')

Scenario: sub protocol
    Given def demoBaseUrl = 'wss://subscriptions.graph.cool/v1/cizfapt9y2jca01393hzx96w9'
    And def handler = function(msg){ karate.signal(msg) }
    And def socket = karate.webSocket(demoBaseUrl, 'graphql-subscriptions', handler)
    And def txt = '{"type": "connection_init", "payload": {}}'
    When eval socket.send(txt)
    And def result = karate.listen(5000)
    Then match result == { type: 'connection_ack' }
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
    And def socket = karate.webSocket('ws://echo.websocket.org', null, handler)
    And bytes data = read('../upload/test.pdf')
    When eval socket.sendBytes(data)
    And def result = karate.listen(5000)
    # the result data-type is byte-array, but this comparison works
    Then match result == read('../upload/test.pdf')
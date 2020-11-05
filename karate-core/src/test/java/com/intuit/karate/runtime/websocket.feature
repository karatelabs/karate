Feature:

Scenario:
* def handler = function(msg){ karate.log('msg:', msg); return msg.startsWith('hello') }
* def socket = karate.webSocket('ws://echo.websocket.org', handler)
* socket.send('hello world!')
* def result = socket.listen(5000)
* match result == 'hello world!'

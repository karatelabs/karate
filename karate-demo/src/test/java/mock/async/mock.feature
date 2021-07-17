Feature:

Background:
* def QueueUtils = Java.type('mock.async.QueueUtils')

Scenario: pathMatches('/send')
* QueueUtils.send('first', 100)
* QueueUtils.send('second', 200)
* QueueUtils.send('third', 300)
* def response = { success: true }

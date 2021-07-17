Feature:

Scenario:
* def QueueConsumer = Java.type('mock.async.QueueConsumer')
* def queue = new QueueConsumer()
* queue.listen(karate)

* def port = karate.start('mock.feature').port
* url 'http://localhost:' + port
* path 'send';
* method get
* status 200

* java.lang.Thread.sleep(1000)
* def messages = karate.signalCollect()
* match messages == ['first', 'second', 'third']

Feature:

Scenario:
* def QueueConsumer = Java.type('mock.async.QueueConsumer')
# this will start listening to messages and collecting them
* def queue = new QueueConsumer()

* def port = karate.start('mock.feature').port
* url 'http://localhost:' + port
* path 'send';
* method get
* status 200

# * java.lang.Thread.sleep(1000)
# * def messages = queue.collect()

# smarter wait instead of the above two lines
* def messages = queue.waitUntilCount(3)

* match messages == ['first', 'second', 'third']

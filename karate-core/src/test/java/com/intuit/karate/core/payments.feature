Feature:

Scenario:
* url 'http://localhost:8080/api/payments'

* request { amount: 5.67, description: 'test one' }
* method post
* status 200
* match response == { id: '#string', amount: 5.67, description: 'test one' }
* def id = response.id

* path id
* method get
* status 200
* match response == { id: '#(id)', amount: 5.67, description: 'test one' }

* path id
* request { id: '#(id)', amount: 5.67, description: 'test two' }
* method put
* status 200
* match response == { id: '#(id)', amount: 5.67, description: 'test two' }

* method get
* status 200
* match response contains { id: '#(id)', amount: 5.67, description: 'test two' }

* path id
* method delete
* status 200

* path id
* method get
* status 404

* method get
* status 200
* match response !contains { id: '#(id)', amount: '#number', description: '#string' }

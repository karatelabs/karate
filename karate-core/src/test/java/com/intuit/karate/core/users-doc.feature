Feature:

@one
Scenario:
* url 'https://jsonplaceholder.typicode.com/users'
* method get
* doc { read: 'users.html' }

* path '1'
* method get
* doc { read: 'users-single.html' }

@one @two
Scenario:
# some comment
* print 'in second'
# another comment
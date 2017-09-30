Feature: not equals tests

Scenario:

* def expected = { foo: '#number' }
* def test = { foo: 'bar' }
* match test != { foo: 'baz' }
* match test != null
* match test != 1
* match test != true
* match test != 'foo'
* match test != []
* match test != {}
* match test != '#array'
* match test != '#(expected)'
* match test != '#(^expected)'
* match test != '#(^^expected)'
* match test != '#(!^test)'

* def expected = 'bar'
* def test = 'foo'
* match test != null
* match test != 1
* match test != true
* match test != 'bar'
* match test != []
* match test != {}
* match test != '#array'
* match test != '#(expected)'
* match test != '#regex .{2}'
* match test != '#? _.length == 2'

* def test = [1, 2]
* match test != '#[1]'
* match test != '#[]? _ > 2'



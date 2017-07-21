Feature: various javascript tests

Scenario: arrays returned from js can be used in match
* def fun = function(){ return ['foo', 'bar', 'baz'] }
* def json = ['foo', 'bar', 'baz']
* match json == fun()
* def expected = fun()
* match json == expected

Scenario: json-path can be performed in js
* def json = [{foo: 1}, {foo: 2}]
* def fun = function(arg) { return karate.jsonPath(arg, '$[*].foo') }
* def res = call fun json
* match res == [1, 2]

Scenario: table to json with expressions evaluated
* def one = 'hello'
* def two = { baz: 'world' }
* table json =
    | foo     | bar |
    | one     | 1   |
    | two.baz | 2   |
* match json == [{ foo: 'hello', bar: 1 }, { foo: 'world', bar: 2 }]

Scenario: json path with keys with spaces or other troublesome characters
* def json = { 'sp ace': 'foo', 'hy-phen': 'bar', 'full.stop': 'baz' }
* string jsonString = json
* match jsonString == '{"sp ace":"foo","hy-phen":"bar","full.stop":"baz"}'
# get comes to the rescue because spaces are a problem on the LHS
* def val1 = get json $['sp ace']
* match val1 == 'foo'
* match json['hy-phen'] == 'bar'
* match json['full.stop'] == 'baz'

Scenario: pass json var (becomes a map) to a function
* def json = { foo: 'bar', hello: 'world' }
* def fun = function(o){ return o }
* def result = call fun json
* match result == json

Scenario: remove json key from js
* def json = { foo: 'bar', hello: 'world' }
* def fun = function(){ karate.remove('json', '$.foo') }
* call fun
* match json == { hello: 'world' }

Scenario: remove json value
* def data = { a: 'hello', b: null, c: null }
* def json = { foo: '#(data.a)', bar: '#(data.b)', baz: '##(data.c)' }
* match json == { foo: 'hello', bar: null }

Scenario: optional json values
* def response = [{a: 'one', b: 'two'}, { a: 'one' }]
* match each response contains { a: 'one', b: '##("two")' }

Scenario: get and json path
* def foo = { bar: { baz: 'ban' } }
* def res = get foo $..bar[?(@.baz)]
* match res contains { baz: 'ban' }

Scenario: comparing 2 payloads
* def foo = { hello: 'world', baz: 'ban' }
* def bar = { baz: 'ban', hello: 'world' }
* match foo == bar

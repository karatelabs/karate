Feature: various javascript tests

Scenario: arrays returned from js can be used in match
* def fun = function(){ return ['foo', 'bar', 'baz'] }
* def json = ['foo', 'bar', 'baz']
* match json == fun()
* def expected = fun()
* match json == expected

Scenario: arrays returned from js can be modified using 'set'
* def fun = function(){ return [{a: 1}, {a: 2}, {b: 3}] }
* def json = fun()
* set json[1].a = 5
* match json == [{a: 1}, {a: 5}, {b: 3}]

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

Scenario: table to json with nested json
* def one = 'hello'
* def two = { baz: 'world' }
* table json =
    | foo     | bar            |
    | one     | { baz: 1 }     |
    | two.baz | ['baz', 'ban'] |
    | true    | one == 'hello' |
* print json
* match json == [{ foo: 'hello', bar: { baz: 1 } }, { foo: 'world', bar: ['baz', 'ban'] }, { foo: true, bar: true }]

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

Scenario: js eval
* def temperature = { celsius: 100, fahrenheit: 212 }
* string expression = 'temperature.celsius'
* def celsius = karate.eval(expression)
* assert celsius == 100
* string expression = 'temperature.celsius * 1.8 + 32'
* match temperature.fahrenheit == karate.eval(expression)

Scenario: js and numbers - float vs int
* def foo = parseInt('10')
* string json = { bar: '#(foo)' }
* match json == '{"bar":10.0}'
* string json = { bar: '#(~~foo)' }
* match json == '{"bar":10}'

Scenario: js match is strict for data types
* def foo = { a: '5', b: 5, c: true, d: 'true' }
* match foo !contains { a: 5 }
* match foo !contains { b: '5' }
* match foo !contains { c: 'true' }
* match foo !contains { d: true }
* match foo == { a: '5', b: 5, c: true, d: 'true' }

Scenario: json path in expressions
* table data =
    | a | b   |
    | 1 | 'x' |
    | 2 | 'y' |
* def foo = [{a: 1, b: 'x'}, {a: 2, b: 'y'}]
* match data == foo
* match foo == data
* match foo[*].a == [1, 2]
# the $ prefix indicates a path expression on a variable, it behaves like 'get'
* match foo[*].a == $data[*].a
* match foo[*].b == $data[*].b

Scenario: get combined with array index
* def foo = [{a: 1, b: 'x'}, {a: 2, b: 'y'}]
* def first = get[0] foo[*].a
* match first == 1
* match first == get[0] foo[*].a

Scenario: set via table
* def cat = { name: '' }
* set cat
| path   | value |
| name   | 'Bob' |
| age    | 5     |
* match cat == { name: 'Bob', age: 5 }

Scenario: set nested via table
* def cat = { name: 'Wild', kitten: null }
* set cat $.kitten
| path   | value |
| name   | 'Bob' |
| age    | 5     |
* match cat == { name: 'Wild', kitten: { name: 'Bob', age: 5 } }

Scenario: set variable plus path via table
* def cat = { name: 'Wild', kitten: null }
* set cat.kitten
| path   | value |
| name   | 'Bob' |
| age    | 5     |
* match cat == { name: 'Wild', kitten: { name: 'Bob', age: 5 } }

Scenario Outline: examples and optional json keys
* def search = { name: { first: "##(<first>)", last: "##(<last>)" }, age: "##(<age>)" }
* match search == <expected>

Examples:
| first  | last    | age  | expected                                            |
| 'John' | 'Smith' | 20   | { name: { first: 'John', last: 'Smith' }, age: 20 } |
| 'Jane' | 'Doe'   | null | { name: { first: 'Jane', last: 'Doe' } }            |
| null   | 'Waldo' | null | { name: { last: 'Waldo' } }                         |
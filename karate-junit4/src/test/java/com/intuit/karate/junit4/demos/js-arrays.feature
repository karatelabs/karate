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
    * table json
        | foo     | bar |
        | one     | 1   |
        | two.baz | 2   |
    * match json == [{ foo: 'hello', bar: 1 }, { foo: 'world', bar: 2 }]

Scenario: table to json with nested json
    * def one = 'hello'
    * def two = { baz: 'world' }
    * table json
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

Scenario: remove json key using keyword
    * def json = { foo: 'bar', hello: 'world' }
    * remove json $.foo
    * match json == { hello: 'world' }

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
    * table data
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
                        
Scenario: set via table where variable does not exist
    note how karate will create parent paths if needed

    * set foo
    | path | value      |
    | bar  | 'baz'      |
    | a.b  | 'c'        |
    | fizz | { d: 'e' } |
    * match foo == { bar: 'baz', a: { b: 'c' }, fizz: { d: 'e' } }

Scenario: set via table with fancy array paths and multi-dimensional arrays
    * set foo
    | path   | value   |
    | bar[0] | 'baz'   |
    | a[0].b | 'ban'   |
    | c[0]   | [1, 2]  | 
    | c[1]   | [3, 4]  |   
    * match foo == { bar: [ 'baz'], a: [{ b: 'ban' }], c: [[1, 2], [3, 4]] }

Scenario: set array via table where variable does not exist
    * set foo
    | path | 0     |
    | bar  | 'baz' |
    * match foo == [{ bar: 'baz' }]

Scenario: set array via table, multiple items, var does not exist
    * set foo
    | path | 0     | 1     |
    | bar  | 'baz' | 'ban' |
    * match foo == [{ bar: 'baz' }, { bar: 'ban' }]

Scenario: set array via table, var exists, indexes specified
    the column headings are used as indexes

    * def foo = [{ bar: 'a' }, { bar: 'b' }, { bar: 'c' }, { bar: 'd' }]
    * set foo
    | path | 3     | 1     |
    | bar  | 'baz' | 'ban' |
    * match foo == [{ bar: 'a' }, { bar: 'ban' }, { bar: 'c' }, { bar: 'baz' }]

Scenario: set array via table, var does not exist, no array indexes
    if the column headings are not integers, karate uses the column position
    but column headings have to be unique, they can be used to describe the column effectively, but are otherwise useless

    * set foo
    | path | one   | two   |
    | bar  | 'baz' | 'ban' |
    * match foo == [{ bar: 'baz' }, { bar: 'ban' }]

Scenario: set via table, var does not exist, different nesting options
    * set first
    | path | value          |
    | one  | { bar: 'baz' } |
    | two  | { bar: 'ban' } |
    * match first == { one: { bar: 'baz' }, two: { bar: 'ban' } }

* set second
    | path     | value |
    | one.bar  | 'baz' |
    | two.bar  | 'ban' |
    * match second == first

Scenario Outline: examples and optional json keys
    * def search = { name: { first: "##(<first>)", last: "##(<last>)" }, age: "##(<age>)" }
    * match search == <expected>

    Examples:
    | first  | last    | age  | expected                                            |
    | 'John' | 'Smith' | 20   | { name: { first: 'John', last: 'Smith' }, age: 20 } |
    | 'Jane' | 'Doe'   | null | { name: { first: 'Jane', last: 'Doe' } }            |
    | null   | 'Waldo' | null | { name: { last: 'Waldo' } }                         |

Scenario: set via table, since blanks are skipped, this is cleaner than the above
    approach of using a scenario outline and examples

    * set search
        | path       | 0        | 1      | 2       |
        | name.first | 'John'   | 'Jane' |         |
        | name.last  | 'Smith'  | 'Doe'  | 'Waldo' |
        | age        | 20       |        |         |

    * match search[0] == { name: { first: 'John', last: 'Smith' }, age: 20 }
    * match search[1] == { name: { first: 'Jane', last: 'Doe' } }
    * match search[2] == { name: { last: 'Waldo' } } 

Scenario: expressions are allowed, and the default behavior is to skip anything that evaluates to null.
    this can be over-ridden by simply enclosing the expression in parentheses

    * table data
        | first  | last    | age  |
        | 'John' | 'Smith' | 20   |
        | 'Jane' | 'Doe'   |      |
        |        | 'Waldo' |      |

    # for column 2, note how the null is retained for name.first
    * set search
        | path       | 0             | 1             | 2               |
        | name.first | data[0].first | data[1].first | (data[2].first) |
        | name.last  | data[0].last  | data[1].last  | data[2].last    |
        | age        | data[0].age   | data[1].age   | data[2].age     |

    * match search[0] == { name: { first: 'John', last: 'Smith' }, age: 20 }
    * match search[1] == { name: { first: 'Jane', last: 'Doe' } }
    * match search[2] == { name: { first: null, last: 'Waldo' } } 

Scenario: just to be clear about how to set a null if really needed in the resulting json
    * set foo
        | path       | value  |
        | name.first | null   |
        | name.last  | (null) |
        | age        |        |
    
    * match foo == { name: { last: null } }

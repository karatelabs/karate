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

Scenario: json behaves like a java map within functions (will change with graal)
    * def payload = { a: 1, b: 2 }
    * def keys = function(o){ return o.keySet() }
    * def values = function(o){ return o.values() }
    * def size = function(o){ return o.size() }
    * json result = keys(payload)
    * match result == ['a', 'b']
    * json result = values(payload)
    * match result == [1, 2]
    * def length = size(payload)
    * match length == 2

Scenario: json-path can be performed in js
    * def json = [{foo: 1}, {foo: 2}]
    * def fun = function(arg) { return karate.jsonPath(arg, '$[*].foo') }
    * def res = call fun json
    * match res == [1, 2]
    # json-path in the form $varname.blah
    * def foo = { bar: [{baz: 1}, {baz: 2}, {baz: 3}]}
    * def fun = function(){ return karate.get('$foo.bar[*].baz') }
    * def res = call fun
    * match res == [1, 2, 3]

Scenario: set via json-path can be done in js
    * def json = { foo: [] }
    * karate.set('json', '$.foo[]', { bar: 'baz' })
    * match json == { foo: [{ bar: 'baz' }] }

Scenario: ensure nested nashorn arrays convert correctly
    * def actual = ({ a: [1, 2, 3] })
    * match actual == { a: [1, 2, 3] }

Scenario: karate forEach operation on lists
    * def res = []
    * def fun = function(x){ karate.appendTo(res, x * x) }
    * def list = [1, 2, 3]
    * karate.forEach(list, fun)
    * match res == [1, 4, 9]

Scenario: karate map operation
    * def fun = function(x){ return x * x }
    * def list = [1, 2, 3]
    * def res = karate.map(list, fun)
    * match res == [1, 4, 9]

Scenario: convert an array into a different shape
    * def before = [{ foo: 1 }, { foo: 2 }, { foo: 3 }]
    * def fun = function(x){ return { bar: x.foo } }
    * def after = karate.map(before, fun)
    * match after == [{ bar: 1 }, { bar: 2 }, { bar: 3 }]

Scenario: karate filter operation
    * def fun = function(x){ return x % 2 == 0 }
    * def list = [1, 2, 3, 4]
    * def res = karate.filter(list, fun)
    * match res == [2, 4]

Scenario: karate forEach operation on maps
    * def keys = []
    * def vals = []
    * def idxs = []
    * def fun = 
    """
    function(x, y, i) { 
      karate.appendTo(keys, x); 
      karate.appendTo(vals, y); 
      karate.appendTo(idxs, i); 
    }
    """
    * def map = { a: 2, b: 4, c: 6 }
    * karate.forEach(map, fun)
    * match keys == ['a', 'b', 'c']
    * match vals == [2, 4, 6]
    * match idxs == [0, 1, 2]

Scenario: karate filter operation, using array indexes
    * def fun = function(x, i){ return i % 2 == 0 }
    * def list = [1, 2, 3, 4]
    * def res = karate.filter(list, fun)
    * match res == [1, 3]

Scenario: karate find index of first match (primitive)
    * def list = [1, 2, 3, 4]
    * def searchFor = 3
    * def foundAt = []
    * def fun = function(x, i){ if (x == searchFor) karate.appendTo(foundAt, i) }
    * karate.forEach(list, fun)
    * match foundAt == [2]

Scenario: karate find index of first match (complex)
    * def list = [{ a: 1, b: 'x'}, { a: 2, b: 'y'}, { a: 3, b: 'z'}]
    * def searchFor = { a: 2, b: '#string'}
    * def foundAt = []
    * def fun = function(x, i){ if (karate.match(x, searchFor).pass) karate.appendTo(foundAt, i) }
    * karate.forEach(list, fun)
    * match foundAt == [1]

Scenario: map with key - for the common case of converting arrays of primitives into arrays of objects
    * def list = [ 'Bob', 'Wild', 'Nyan' ]
    * def data = karate.mapWithKey(list, 'name')
    * match data == [{ name: 'Bob' }, { name: 'Wild' }, { name: 'Nyan' }]

    * def list = [ 1, 2, 3]
    * def data = karate.mapWithKey(list, 'val')
    * match data == [{ val: 1 }, { val: 2 }, { val: 3 }]

    # will actually work for any kind of list
    * def list = [{ a: 1 }, { b: 2 }]
    * def data = karate.mapWithKey(list, 'foo')
    * match data == [{ foo: { a: 1 } }, { foo: { b: 2 } }]

    # should work for null edge case
    * def list = null
    * def data = karate.mapWithKey(list, 'foo')
    * match data == []

Scenario: filterKeys
    * def schema = { a: '#string', b: '#number', c: '#boolean' }
    * def response = { a: 'x', c: true }
    # very useful for validating a response against a schema "super-set"
    * match response == karate.filterKeys(schema, response)
    * match karate.filterKeys(response, 'b', 'c') == { c: true }
    * match karate.filterKeys(response, ['a', 'b']) == { a: 'x' }

Scenario: merge
    * def foo = { a: 1 }
    * def bar = karate.merge(foo, { b: 2 })
    * match bar == { a: 1, b: 2 }

Scenario: append
    * def foo = [{ a: 1 }]
    * def bar = karate.append(foo, { b: 2 })
    * match bar == [{ a: 1 }, { b: 2 }]
    * def foo = { a: 1 }
    * def bar = karate.append(foo, { b: 2})
    * match bar == [{ a: 1 }, { b: 2 }]
    # ensure this works even within js
    * def fun = function(){ var x = [1, 2]; return karate.append(x, 3, 4) }
    * match fun() == [1, 2, 3, 4]

Scenario: simplest way to get the size of a json object
    * def json = { a: 1, b: 2, c: 3 }
    * def map = karate.toBean(json, 'java.util.HashMap')
    * def count = map.size()
    * match count == 3

Scenario: get last array element (js)
    * def list = [1, 2, 3, 4]
    * def last = list[list.length-1]
    * match last == 4

Scenario: get last array element (json-path)
    * def list = [1, 2, 3, 4]
    * def last = get[0] list[-1:]
    * match last == 4

Scenario: advanced json-path that the jayway implementation has limitations with
    * def response = read('products.json')
    * def result = $[?(@.partIDs[?(@.id == 1)])]
    # should be 2
    * match result == '#[3]'

Scenario: work around for the above
    * def hasId = 
        """
        function(product, id) {
            return karate.jsonPath(product, '$.partIDs[?(@.id==' + id + ')]').length;
        }
        """
    * def products = read('products.json')
    * def result = []
    * karate.repeat(products.length, function(i){ if (hasId(products[i], 1)) result.add(products[i]) })
    * match result[*].name == ['Wotsit v1.5', 'Wotsit v2.5']

Scenario: work around but using karate.filter
    * def id = 1
    * def hasId = function(x){ return karate.jsonPath(x, '$.partIDs[?(@.id==' + id + ')]').length }
    * def products = read('products.json')
    * def result = karate.filter(products, hasId)
    * match result[*].name == ['Wotsit v1.5', 'Wotsit v2.5']

Scenario: table to json with expressions evaluated
    * def one = 'hello'
    * def two = { baz: 'world' }
    * table json
        | foo     | bar |
        | one     | 1   |
        | two.baz | 2   |
    * match json == [{ foo: 'hello', bar: 1 }, { foo: 'world', bar: 2 }]

Scenario: table to json with expressions and empty / nulls
    * def one = { baz: null }
    * table json
        | foo     | bar    |
        | 'hello' |        |
        | one.baz | (null) |
        | 'world' | null   |
    * match json == [{ foo: 'hello' }, { bar: null }, { foo: 'world' }]

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
    * match each response contains { a: 'one', b: '##string' }

Scenario: #null, ##null, #present and #notpresent
    * def foo = { }
    * match foo != { a: '#present' }
    * match foo == { a: '#notpresent' }
    * match foo == { a: '#ignore' }
    * match foo == { a: '##null' }
    * match foo != { a: '#null' }
    * match foo != { a: '#notnull' }
    * match foo == { a: '##notnull' }
    * match foo != { a: null }

    * def foo = { a: null }
    * match foo == { a: null }
    * match foo == { a: '#null' }    
    * match foo == { a: '##null' }
    * match foo != { a: '#notnull' }
    * match foo != { a: '##notnull' }
    * match foo == { a: '#present' }
    * match foo == { a: '#ignore' }
    * match foo != { a: '#notpresent' }

    * def foo = { a: 1 }
    * match foo == { a: 1 }
    * match foo == { a: '#number' }
    * match foo == { a: '#notnull' }
    * match foo == { a: '##notnull' }
    * match foo != { a: '#null' }    
    * match foo != { a: '##null' }
    * match foo == { a: '#present' }
    * match foo == { a: '#ignore' }
    * match foo != { a: '#notpresent' }

Scenario: alternative notpresent check using json-path
    * def foo = { a: 1 }
    * match foo.a == '#present'
    * match foo.nope == '#notpresent'

Scenario: regression test for edge case in fuzzy matches
    * def answer = { foo: 'foo', bar: 'bar', baz': 'baz' }
    * match answer != { foo: '#string', foobar: '#notpresent', foobaz': '#notpresent' }
    * match answer != { foo: '#string', foobar: '##string', foobaz': '##string' }

Scenario: get and json path
    * def foo = { bar: { baz: 'ban' } }
    * def res = get foo $..bar[?(@.baz)]
    * match res contains { baz: 'ban' }

Scenario: comparing 2 payloads
    * def foo = { hello: 'world', baz: 'ban' }
    * def bar = { baz: 'ban', hello: 'world' }
    * match foo == bar

Scenario: [contains deep] will recurse nested json
    * def original = { a: 1, b: 2, c: 3, d: { a: 1, b: 2 } }
    * def expected = { a: 1, c: 3, d: { b: 2 } }
    * match original !contains expected
    * match original contains deep expected

Scenario: [contains deep] will recurse nested array
    * def original = { a: 1, arr: [ { b: 2, c: 3 }, { b: 3, c: 4 } ] }
    * def expected = { a: 1, arr: [ { b: 2 }, { c: 4 } ] }
    * match original !contains expected    
    * match original contains deep expected

Scenario: [contains deep] should not recurse in reverse
    * def original = { "a": { "b": { "c": { "d":1, "e":2 } } } }
    * def compared = { "a": { "b": { "c": { "d":1, "e":2, "f":3 } } } }
    * match original !contains compared
    * match compared !contains original
    # * match original !contains deep compared
    * match compared contains deep original

Scenario: contains deep should support multi-line / docstring r.h.s
    * def message =
      """
      {
          order_id: 5,
          products: [
            { product_id: 100, name: "bicycle" },
            { product_id: 101, name: "car" }
          ]
      }
      """
    * match message contains deep
      """
      {
          order_id: 5,
          products: [
            { product_id: 101, name: "car" },
            { product_id: 100, name: "bicycle" }
          ]
      }
      """

Scenario: js eval
    * def temperature = { celsius: 100, fahrenheit: 212 }
    * string expression = 'temperature.celsius'
    * def celsius = karate.eval(expression)
    * assert celsius == 100
    * string expression = 'temperature.celsius * 1.8 + 32'
    * match temperature.fahrenheit == karate.eval(expression)

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

Scenario: set via table, complex paths
    * set expected
    | path            | value   |
    | first           | 'hello' |
    | client.id       | 'goodbye'            |
    | client.foo.bar  | 'world' |
    * match expected == { first: 'hello', client: { id: 'goodbye', foo: { bar: 'world' }}}

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

Scenario: set via table, repeated paths at the top
    * set foo.bar
    | path   | value |
    | one    | 1     |
    | two[0] | 2     |
    | two[1] | 3     |

    * match foo == { bar: { one: 1, two: [2, 3] } }

Scenario Outline: examples and optional json keys (see outline.feature for a better version)
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

Scenario: read json within a js function
    * def fun = function(){ var temp = read('classpath:test.json'); return temp.error[1].id }
    * def val = call fun
    * match val == 2

Scenario: contains / not contains
    * def some = [1, 2]
    * def actual = [1, 2, 3]
    * def none = [4, 5]
    * match actual contains some
    * match actual == '#(^some)'
    * match actual !contains none
    * match actual == '#(!^none)'

Scenario: match in js
    * def foo = { hello: 'world' }
    * def res = karate.match(foo, { hello: '#string' } )
    * match res == { pass: true, message: null }
    * def res = karate.match(foo, { hello: '#number' } )
    * match res == { pass: false, message: '#notnull' }

Scenario: advanced match in js
    * def foo = { a: 1, b: 'foo' }
    * def res = karate.match("foo contains { a: '#number' }")
    * match res == { pass: true, message: null }
    * def res = karate.match("foo == { a: '#number' }")
    * match res == { pass: false, message: '#notnull' }
    * def foo = [1, 2, 3]
    * def res = karate.match("each foo == '#number'")
    * match res == { pass: true, message: null }

Scenario: karate.os
    * def temp = karate.os
    * print 'os:', temp
    * match temp == { type: '#string', name: '#string' }

Scenario: using the java contains api (will change with graal)
    * def allowed = ['Music', 'Entertainment', 'Documentaries', 'Family']
    * def actual = ['Entertainment', 'Family']
    * match each actual == '#? allowed.contains(_)'

Scenario: using the java indexOf api (will change with graal)
    * def response = [{ name: 'a' }, { name: 'b' }, { name: 'c' }]
    * def names = $[*].name
    * def index = names.indexOf('b')
    * match index == 1

Scenario: karate.forEach() and js arguments (may change with graal)
    * def vals = []
    * def fun = function(){ karate.forEach(arguments, function(k, v){ vals.add(v) }) }
    * fun('a', 'b', 'c')
    * match vals == ['a', 'b', 'c']

Scenario: lists - karate.sizeOf() keysOf() valuesOf() appendTo()
    * def foo = [1, 2, 3]
    * match karate.sizeOf(foo) == 3
    * match karate.valuesOf(foo) == [1, 2, 3]
    * def bar = karate.appendTo(foo, 4)
    * match foo == [1, 2, 3, 4]
    * match bar == [1, 2, 3, 4]
    * def bar = karate.appendTo(foo, [5, 6])
    * match foo == [1, 2, 3, 4, 5, 6]
    * match bar == [1, 2, 3, 4, 5, 6]
    * def fun = function(){ var x = [1, 2]; return karate.appendTo(x, 3, 4) }
    * match fun() == [1, 2, 3, 4]

Scenario: maps - karate.sizeOf() keysOf() valuesOf() appendTo()
    * def foo = { a: 1, b: 2, c: 3 }
    * match karate.sizeOf(foo) == 3
    * match karate.keysOf(foo) == ['a', 'b', 'c']

Scenario: map and repeat should not mangle js arrays
* def foo = [1, 2]
* def fun = function(x){ return { x: x, bar: [1, 2] } }
* def res = karate.map(foo, fun)
* match res == [{ x: 1, bar: [1, 2]}, { x: 2, bar: [1, 2] }]

* def fun = function(i){ return { foo: [1, 2]} }
* def bar = karate.repeat(2, fun)
* match bar == [{ foo: [1, 2] }, { foo: [1, 2] }]

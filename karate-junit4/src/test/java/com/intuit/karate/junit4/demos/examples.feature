Feature:

Scenario:

Given def cat = 
"""
{
  name: 'Billie',
  kittens: [
      { id: 23, name: 'Bob' },
      { id: 42, name: 'Wild' }
  ]
}
"""
Then match cat.kittens[0] == { name: 'Bob', id: 23 }
Then match cat.kittens[0].id == 23
Then match cat.kittens[*].id == [23, 42]
Then match cat.kittens[*].id contains 23
Then match cat.kittens[*].id contains [42, 23]
Then match cat..name == ['Billie', 'Bob', 'Wild']
Then match each cat.kittens contains { id: '#number' }
Then match each cat.kittens == { id: '#notnull', name: '#regex [A-Z][a-z]+' }

* def isLessThanFifty = function(x) { return x < 50 } // >
Then match each cat.kittens contains { id: '#? isLessThanFifty(_)' }

* def expected = [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]
* match cat == { name: 'Billie', kittens: '#(^^expected)' }

# find single kitten where id == 23
* def bob =  get[0] cat.kittens[?(@.id==23)]
* match bob.name == 'Bob'

# using the karate object if the expression is dynamic
* def temp =  karate.jsonPath(cat, "$.kittens[?(@.name=='" + bob.name + "')]")
* match temp[0] == bob

# getting the first element of the array returned by a json-path expression in one step
* def temp =  karate.jsonPath(cat, "$.kittens[?(@.name=='" + bob.name + "')]")[0]
* match temp == bob

Given def foo = 42
And def bar = { hello: 'world' }
And def temp = { id: '#(foo)', value: '#(bar.hello)' }
Then match temp == { id: 42, value: 'world' }

Given def temperature = { celsius: 100, fahrenheit: 212 }
Then match temperature contains { fahrenheit: '#($.celsius * 1.8 + 32)' }

When def user = { name: 'john', age: 21 }
And def lang = 'en'

* def embedded = { name: '#(user.name)', locale: '#(lang)', sessionUser: '#(user)'  }
* def enclosed = ({ name: user.name, locale: lang, sessionUser: user  })
* match embedded == enclosed

Given def user = { name: 'john', age: 21 }
And def lang = 'en'
When def session = { name: '#(user.name)', locale: '#(lang)', sessionUser: '#(user)'  }
Then match session == { name: 'john', locale: 'en', sessionUser: { name: 'john', age: 21 } }

Given def user = <user><name>john</name></user>
And def lang = 'en'
When def session = <session><locale>#(lang)</locale><sessionUser>#(user)</sessionUser></session>
Then match session == <session><locale>en</locale><sessionUser><user><name>john</name></user></sessionUser></session>

* def actual = 23
# so instead of this
* def kitnums = get cat.kittens[*].id
* match actual == kitnums[0]

# you can do this in one line
* match actual == get[0] cat.kittens[*].id

# get short cuts
* def kitnums = $cat.kittens[*].id
* match kitnums == [23, 42]
* def kitnames = $cat.kittens[*].name
* match kitnames == ['Bob', 'Wild']

# the above can be condensed to
* match cat.kittens[*].id == [23, 42]
* match cat.kittens[*].name == ['Bob', 'Wild']

# or if you prefer using 'pure' JsonPath
* match cat $.kittens[*].id == [23, 42]
* match cat $.kittens[*].name == ['Bob', 'Wild']

* def LocalDateTime = Java.type('java.time.LocalDateTime')
* def createDate = LocalDateTime.now() + ''
* def expiryDate = LocalDateTime.now().plusMinutes(5) + ''
* def testRequest = { createDateTime: '#(createDate)', expiryDate: '#(expiryDate)' }
* print karate.pretty(testRequest)

# contains and arrays
* def response =
"""
[
    {
        "a": "a",
        "b": "a",
        "c": "a",
    },
    {
        "a": "ab",
        "b": "ab",
        "c": "ab",
    }
]
"""
* match response[1] contains { b: 'ab' }
* match response contains { a: 'ab', b: 'ab', c: 'ab' }
* match response contains { a: '#ignore', b: 'ab', c: '#notnull' }

# in-line macro
* def expected = { b: 'ab' }
* match response contains '#(^expected)'

# json path, probably the best option
* match response[*].b contains 'ab'

# json path
* def temp = get response $[?(@.b=='ab')]
* assert temp.length == 1
* match temp == '#[1]'

# json path one liner
* match response $[?(@.b=='ab')] == '#[1]'
* match response[?(@.b=='ab')] == '#[1]'
* match $[?(@.b=='ab')] == '#[1]'

# conditional logic
* def zone = 'zone1'
* def filename = (zone == 'zone1' ? 'test1.feature' : 'test2.feature')
* match filename == 'test1.feature'

* def response = { foo: 'bar' }
* def expected = (zone == 'zone1' ? { foo: '#string' } : { bar: '#number' })
* match response == expected

* def temp = 'before'
* if (zone == 'zone1') karate.set('temp', 'after')
* match temp == 'after'

* eval
"""
var foo = function(v){ return v * v };
var nums = [0, 1, 2, 3, 4];
var squares = [];
for (var n in nums) {
  squares.push(foo(n));
}
karate.set('temp', squares);
"""
* match temp == [0, 1, 4, 9, 16]

* def json = { a: 1 }
* def key = 'b'
# use dynamic path expressions to mutate json
* json[key] = 2
* match json == { a: 1, b: 2 }
* karate.remove('json', '$.' + key)
* match json == { a: 1 }
* karate.set('json', '$.c[]', { d: 'e' })
* match json == { a: 1, c: [{ d: 'e' }] }

# #null and #notpresent
* def foo = { }
* match foo == { a: '##null' }
* match foo == { a: '##notnull' }
* match foo == { a: '#notpresent' }
* match foo == { a: '#ignore' }

* def foo = { a: null }
* match foo == { a: '#null' }    
* match foo == { a: '##null' }
* match foo == { a: '#present' }
* match foo == { a: '#ignore' }

* def foo = { a: 1 }
* match foo == { a: '#notnull' }
* match foo == { a: '##notnull' }
* match foo == { a: '#present' }
* match foo == { a: '#ignore' }

# json-path filter
* def expected = [{ entityId: 'foo'}, { entityId: 'bar'}]

* def temp = $expected[?(@.entityId=='foo')]
* match temp == [{ entityId: 'foo'}]

* def entityId = 'foo'
* def temp = karate.jsonPath(expected, "$[?(@.entityId=='foo')]")
* match temp == [{ entityId: 'foo'}]

# using java for a case-insensitive string comparison
* def equalsIgnoreCase = function(a, b){ return a.equalsIgnoreCase(b) }
* assert equalsIgnoreCase('hello', 'HELLO')
* def foo = { message: 'HELLO' }
* match foo == { message: '#? equalsIgnoreCase(_, "hello")' }

# csv conversion
* text foo =
    """
    name,type
    Billie,LOL
    Bob,Wild
    """
* csv bar = foo
* match bar == [{ name: 'Billie', type: 'LOL' }, { name: 'Bob', type: 'Wild' }]

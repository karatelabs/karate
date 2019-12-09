Feature: json-schema like validation

Scenario: but simpler and more powerful

* def response = read('odds.json')

* def oddSchema = { price: '#string', status: '#? _ < 3', ck: '##number', name: '#regex[0-9X]' }
* def isValidTime = read('time-validator.js')

Then match response ==
"""
{ 
  id: '#regex[0-9]+',
  count: '#number',
  odd: '#(oddSchema)',
  data: { 
    countryId: '#number', 
    countryName: '#string', 
    leagueName: '##string', 
    status: '#number? _ >= 0', 
    sportName: '#string',
    time: '#? isValidTime(_)'
  },
  odds: '#[] oddSchema'  
}
"""
# other examples

# should be an array
* match $.odds == '#[]'

# should be an array of size 4
* match $.odds == '#[4]'

# optionally present (or null) and should be an array of size greater than zero
* match $.odds == '##[_ > 0]'

# should be an array of size equal to $.count
* match $.odds == '#[$.count]'

# use a predicate function to validate each array element
* def isValidOdd = function(o){ return o.name.length == 1 }
* match $.odds == '#[]? isValidOdd(_)'

# for simple arrays, types can be 'in-line'
* def foo = ['bar', 'baz']

# should be an array
* match foo == '#[]'

# should be an array of size 2
* match foo == '#[2]'

# should be an array of strings with size 2
* match foo == '#[2] #string'

# each item of the array should be of length 3
* match foo == '#[]? _.length == 3'

# should be an array of strings each of length 3
* match foo == '#[] #string? _.length == 3'

# should be null or an array of strings
* match foo == '##[] #string'

# each item of the array should match regex (with backslash involved)
* match foo == '#[] #regex \\w+'

# contains
* def actual = [{ a: 1, b: 'x' }, { a: 2, b: 'y' }]

* def schema = { a: '#number', b: '#string' }
* def partSchema = { a: '#number' }
* def badSchema = { c: '#boolean' }
* def mixSchema = { a: '#number', c: '#boolean' }

* def shuffled = [{ a: 2, b: 'y' }, { b: 'x', a: 1 }]
* def first = { a: 1, b: 'x' }
* def part = { a: 1 }
* def mix = { b: 'y', c: true }
* def other = [{ a: 3, b: 'u' }, { a: 4, b: 'v' }]
* def some = [{ a: 1, b: 'x' }, { a: 5, b: 'w' }]

* match actual[0] == schema
* match actual[0] == '#(schema)'

* match actual[0] contains partSchema
* match actual[0] == '#(^partSchema)'

* match actual[0] contains any mixSchema
* match actual[0] == '#(^*mixSchema)'

* match actual[0] !contains badSchema
* match actual[0] == '#(!^badSchema)'

* match each actual == schema
* match actual == '#[] schema'

* match each actual contains partSchema
* match actual == '#[] ^partSchema'

* match each actual contains any mixSchema
* match actual == '#[] ^*mixSchema'

* match each actual !contains badSchema
* match actual == '#[] !^badSchema'

* match actual contains only shuffled
* match actual == '#(^^shuffled)'

* match actual contains first
* match actual == '#(^first)'

* match actual contains any some
* match actual == '#(^*some)'

* match actual !contains other
* match actual == '#(!^other)'

# no in-line equivalent !
* match actual contains '#(^part)'

# no in-line equivalent !
* match actual contains '#(^*mix)'

* assert actual.length == 2
* match actual == '#[2]'

Scenario: complex nested arrays
* def json =
"""
{
  "foo": {
    "bars": [
      { "barOne": "dc", "barTwos": [] },
      { "barOne": "dc", "barTwos": [{ title: 'blah' }] },
      { "barOne": "dc", "barTwos": [{ title: 'blah' }], barThrees: [] },
      { "barOne": "dc", "barTwos": [{ title: 'blah' }], barThrees: [{ num: 1 }] }
    ]
  }
}
"""
* def barTwo = { title: '#string' }
* def barThree = { num: '#number' }
* def bar = { barOne: '#string', barTwos: '#[] barTwo', barThrees: '##[] barThree' }
* match json.foo.bars == '#[] bar'

Scenario: re-usable json chunks as nodes, but optional
* def dogSchema = { id: '#string', color: '#string' }
* def schema = { id: '#string', name: '#string', dog: '##(dogSchema)' }

* def response1 = { id: '123', name: 'foo' }
* match response1 == schema

* def response2 = { id: '123', name: 'foo', dog: { id: '456', color: 'brown' } }
* match response2 == schema

Scenario: pretty print json
* def json = read('odds.json')
* print 'pretty print:\n' + karate.pretty(json)

Scenario: more pretty print
* def myJson = { foo: 'bar', baz: [1, 2, 3]}
* print 'pretty print:\n' + karate.pretty(myJson)

Scenario: various ways of checking that a string ends with a number
* def foo = 'hello1'
* match foo == '#regex hello[0-9]+'
* match foo == '#regex .+[0-9]+'
* match foo contains 'hello'
* assert foo.startsWith('hello')
* def isHello = function(s){ return s.startsWith('hello') && karate.match(s, '#regex .+[0-9]+').pass }
* match foo == '#? isHello(_)'

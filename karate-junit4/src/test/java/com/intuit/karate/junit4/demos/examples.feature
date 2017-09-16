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

Then match cat.kittens[*].id == [23, 42]
Then match cat.kittens[*].id contains 23
Then match cat.kittens[*].id contains [42, 23]
Then match each cat.kittens contains { id: '#number' }
Then match each cat.kittens == { id: '#notnull', name: '#regex [A-Z][a-z]+' }

* def isLessThanFifty = function(x) { return x < 50 } // >
Then match each cat.kittens contains { id: '#? isLessThanFifty(_)' }

* def expected = [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]
* match cat == { name: 'Billie', kittens: '#(^^expected)' }

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

* def actual = 23
# so instead of this
* def kitnums = get cat.kittens[*].id
* match actual == kitnums[0]

# you can do this in one line
* match actual == get[0] cat.kittens[*].id

* def LocalDateTime = Java.type('java.time.LocalDateTime')
* def createDate = LocalDateTime.now() + ''
* def expiryDate = LocalDateTime.now().plusMinutes(5) + ''
* def testRequest = { createDateTime: '#(createDate)', expiryDate: '#(expiryDate)' }
* print karate.pretty(testRequest)


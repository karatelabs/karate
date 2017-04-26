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


Given def foo = 42
And def bar = { hello: 'world' }
And def temp = { id: '#(foo)', value: '#(bar.hello)' }
Then match temp == { id: 42, value: 'world' }

Given def temperature = { celsius: 100, fahrenheit: 212 }
Then match temperature contains { fahrenheit: '#($.celsius * 1.8 + 32)' }




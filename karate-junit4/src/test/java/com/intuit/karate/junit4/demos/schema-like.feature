Feature: json-schema like validation

Scenario: but simpler and more powerful

* def response = read('odds.json')

* def oddSchema = { price: '#string', status: '#? _ < 3', ck: '#number', name: '#regex[0-9X]' }
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
    leagueName: '#string', 
    status: '#number', 
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

# should be an array of size less than 5
* match $.odds == '#[_ < 5]'

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
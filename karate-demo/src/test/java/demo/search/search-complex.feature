@ignore
Feature:

Scenario:
# pre-process call argument
* def search = __arg
* remove search.missing

* url demoBaseUrl
Given path 'search'
And params search
When method get
Then status 200

# '#[1]' means validate if an array, and length is 1
* def exists = function(v){ return v ? '#[1]' : null }

# besides the built-in variable '__arg', each key within it is available by name
# note how the '##' marker is used to auto-remove BEFORE the match
# there are simpler ways to do this, but just for demo
* def expected = { name: '##(exists(name))', country: '##(exists(country))', active: '##(exists(active))', limit: '##(exists(limit))' }
* match response == expected

# demo of how to turn an array of strings into json keys
* def fun =
"""
function(arr) {
  var res = {};
  for (var i = 0; i < arr.length; i++) {
    var key = arr[i];
    res[key] = '#notnull';
  }
  return res;
}
"""
# response should NOT contain a key expected to be missing
* match response !contains fun(missing)

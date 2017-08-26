@ignore
Feature:

Scenario:
* url demoBaseUrl
* def trim = function(v){ return v ? v : null }
Given path 'search'
And params { name: '#(trim(name))', country: '#(trim(country))', active: '#(trim(active))', limit: '#(trim(limit))' }
When method get
Then status 200
# check if an array, and length is 1
* def exists = function(v){ return v ? '#[1]' : null }
# note how the '##' marker is used to auto-remove
* match response == { name: '##(exists(name))', country: '##(exists(country))', active: '##(exists(active))', limit: '##(exists(limit))' }

# the above is an exact equality match, so this is a redundant check, but just for demo
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
* match response !contains fun(missing)
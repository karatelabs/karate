Feature: dynamically creating json for a data-driven test

Background:
* url demoBaseUrl

* def creator = read('../callarray/kitten-create.feature')

* def kittensFn =
"""
function() {
  var out = [];
  for (var i = 0; i < 5; i++) { 
    out.push({ name: 'Kit' + i });
  }
  return out;
}
"""

Scenario: create kittens and validate

* def kittens = call kittensFn
* def result = call creator kittens
* def created = get result[*].response
* assert created.length == 5
* match created[*].name contains [ 'Kit0', 'Kit1', 'Kit2', 'Kit3', 'Kit4' ]




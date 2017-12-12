@ignore
Feature:

Background:
* def nextId = 
"""
function(){ 
  var currentId = karate.get('currentId');
  var nextId = currentId + 1;
  karate.set('currentId', nextId);
  return ~~nextId;
}
"""

Scenario:
* def cat = request
* set cat.id = nextId()
* set cats[] = cat
* def response = cat

@ignore
Feature:

Background:
* def currentId = 0
* def cats = []
* def nextId = 
"""
function(){ 
  var currentId = karate.get('currentId');
  var nextId = currentId + 1;
  karate.set('currentId', nextId);
  return ~~nextId;
}
"""

Scenario: requestMethod == 'POST' && pathMatches('/cats')
    * def cat = request
    * set cat.id = nextId()
    * set cats[] = cat
    * def response = cat

Scenario: requestMethod == 'GET' && pathMatches('/cats')
    * def response = cats

Scenario: requestMethod == 'GET' && pathMatches('/cats/{id}')
    * def id = requestPaths.id
    * def response = cats[id-1]

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


Scenario:
  * def cat = request
  * set cat.id = nextId()
  * set cats[] = cat
  * def response = cat

Scenario: pathMatches('/v10/{somethingSpecific: [\\w]+}') && methodIs(['head', 'get'])
  * def cat = request
  * set cat.id = nextId()
  * set cats[] = cat
  * def response = cat


Scenario: pathMatches('/v10/{something}') && methodIs('get')
  * def cat = request
  * set cat.id = nextId()
  * set cats[] = cat
  * def response = cat

Scenario: pathMatches('/v10/cats')
  * def cat = request
  * set cat.id = nextId()
  * set cats[] = cat
  * def response = cat

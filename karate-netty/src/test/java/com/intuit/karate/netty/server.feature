Feature:

Background:
* def cats = []
* def nextId = call read('increment.js')

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

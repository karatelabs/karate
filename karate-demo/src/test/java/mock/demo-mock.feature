Feature:

Background:
* def nextId = call read('increment.js')
* def cats = {}

Scenario: pathMatches('/cats') && requestMethod == 'POST' && typeContains('xml')
    * def cat = request    
    * def id = nextId()
    * set cat /cat/id = id    
    * set catJson
        | path | value        |
        | id   | id           |
        | name | cat.cat.name |
    * eval cats[id + ''] = catJson
    * def response = cat

Scenario: pathMatches('/cats') && requestMethod == 'POST'
    * def cat = request
    * def id = nextId()
    * set cat.id = id
    * eval cats[id + ''] = cat
    * def response = cat

Scenario: pathMatches('/cats/{id}') && acceptContains('xml')
    * def cat = cats[requestPaths.id]
    * def response = <cat><id>#(cat.id)</id><name>#(cat.name)</name></cat>

Scenario: pathMatches('/cats/{id}')
    * def response = cats[requestPaths.id]

Scenario: pathMatches('/cats/{id}/kittens')
    * def response = cats[requestPaths.id].kittens
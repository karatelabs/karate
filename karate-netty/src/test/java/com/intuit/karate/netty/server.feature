Feature:

Background:
* def cats = []
* def nextId = call read('increment.js')
* configure cors = true

Scenario: pathMatches('/v1/cats') && methodIs('post')
    * def cat = request
    * set cat.id = nextId()
    * set cats[] = cat
    * def response = cat

Scenario: pathMatches('/v1/cats') && methodIs('get')
    * def response = cats

Scenario: pathMatches('/v1/cats/{id}') && methodIs('get')
    * def id = pathParams.id
    * def response = cats[id-1]

Scenario: pathMatches('/v1/body/json') && bodyPath('$.name') == 'Scooby'
    * def response = { success: true }

Scenario: pathMatches('/v1/body/xml') && bodyPath('/dog/name') == 'Scooby'
    * def response = { success: true }

Feature: a helper that validates with its own schema and variables

Scenario:
* def schemas = { item: { id: '#number' } }
* match items == '#[] schemas.item'
* match items[0] == { id: '#(expectedId)' }
* match items contains { id: '#? _ == expectedId' }
* def res = karate.match(items, '#[] schemas.item')
* if (!res.pass) karate.fail(res.message)

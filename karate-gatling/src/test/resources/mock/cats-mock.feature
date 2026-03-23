Feature: Cats API Mock

  Background:
    * def cats = {}
    * def counter = { value: 0 }

  Scenario: pathMatches('/cats/{id}') && methodIs('get')
    * def cat = cats[pathParams.id]
    * def response = cat ? cat : { error: 'Cat not found' }
    * def responseStatus = cat ? 200 : 404

  Scenario: pathMatches('/cats/{id}') && methodIs('put')
    * def cat = cats[pathParams.id]
    * if (cat) cats[pathParams.id] = request
    * if (cat) cats[pathParams.id].id = pathParams.id
    * def response = cat ? cats[pathParams.id] : { error: 'Cat not found' }
    * def responseStatus = cat ? 200 : 404

  Scenario: pathMatches('/cats/{id}') && methodIs('delete')
    * def cat = cats[pathParams.id]
    * if (cat) delete cats[pathParams.id]
    * def response = cat ? { deleted: true } : { error: 'Cat not found' }
    * def responseStatus = cat ? 200 : 404

  Scenario: pathMatches('/cats') && methodIs('post')
    * counter.value = counter.value + 1
    * def id = counter.value + ''
    * cats[id] = request
    * cats[id].id = id
    * def response = cats[id]
    * def responseStatus = 201

  Scenario: pathMatches('/cats') && methodIs('get')
    * def response = karate.valuesOf(cats)
    * def responseStatus = 200

  Scenario: pathMatches('/health')
    * def response = { status: 'UP' }

  Scenario:
    * def responseStatus = 404
    * def response = { error: 'Not found' }

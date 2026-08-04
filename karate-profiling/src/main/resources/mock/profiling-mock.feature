Feature: mock endpoints for profiling workloads

  Background:
    * def cats = {}
    * def nextId = 0

  Scenario: pathMatches('/ping')
    * def response = { success: true }

  Scenario: pathMatches('/cats') && methodIs('post')
    * def id = ~~(nextId = nextId + 1)
    * def cat = request
    * set cat.id = id
    * eval cats[id + ''] = cat
    * def response = cat
    * def responseStatus = 201

  Scenario: pathMatches('/cats/{id}')
    * def response = cats[pathParams.id]
    * def responseStatus = response ? 200 : 404

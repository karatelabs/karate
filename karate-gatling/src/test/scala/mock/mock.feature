Feature: cats stateful crud

  Background:
    * def uuid = function(){ return java.util.UUID.randomUUID() + '' }
    * def cats = {}
    * def delay = function(){ java.lang.Thread.sleep(850) }

  Scenario: pathMatches('/cats') && methodIs('post')
    * def cat = request
    * def id = uuid()
    * cat.id = id
    * cats[id] = cat
    * def response = cat

  Scenario: pathMatches('/cats')
    * def response = $cats.*

  Scenario: pathMatches('/cats/{id}') && methodIs('put')
    * cats[pathParams.id] = request
    * def response = request

  Scenario: pathMatches('/cats/{id}') && methodIs('delete')
    * karate.remove('cats', '$.' + pathParams.id)
    * def response = ''
    * def afterScenario = delay

  Scenario: pathMatches('/cats/{id}')
    * def response = cats[pathParams.id]
    * def responseStatus = response ? 200 : 404

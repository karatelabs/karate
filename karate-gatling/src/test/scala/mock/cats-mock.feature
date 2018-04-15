Feature: cats stateful crud

Background:
* def uuid = function(){ return java.util.UUID.randomUUID() + '' }
* def cats = {}

Scenario: pathMatches('/cats') && methodIs('post')
    * def cat = request
    * def id = uuid()
    * set cat.id = id
    * eval cats[id] = cat
    * def response = cat

Scenario: pathMatches('/cats')
    * def response = $cats.*

Scenario: pathMatches('/cats/{id}')
    * def response = cats[pathParams.id]
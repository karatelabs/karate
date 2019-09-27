@ignore
Feature: stateful mock server
    for help, see: https://github.com/intuit/karate/wiki/ZIP-Release

Background:
* configure cors = true
* def uuid = function(){ return java.util.UUID.randomUUID() + '' }
* def cats = {}

Scenario: pathMatches('/cats') && methodIs('post')
    * def cat = request
    * def id = uuid()
    * cat.id = id
    * cats[id] = cat
    * def response = cat

Scenario: pathMatches('/cats')
    * def response = $cats.*

Scenario: pathMatches('/cats/{id}')
    * def response = cats[pathParams.id]

Scenario: pathMatches('/hardcoded')
    * def response = { hello: 'world' }

Scenario:
    # catch-all
    * def responseStatus = 404
    * def responseHeaders = { 'Content-Type': 'text/html; charset=utf-8' }
    * def response = <html><body>Not Found</body></html>

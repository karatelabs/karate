Feature:

Background:
* def nextId = call read('increment.js')
* def cats = {}
* def requestUrlBase = 'http://127.0.0.1:8080'

Scenario: pathMatches('/greeting') && paramValue('name') != null
    * eval karate.proceed()

Scenario: pathMatches('/greeting')
    * eval karate.proceed()

Scenario: pathMatches('/cats') && methodIs('post') && typeContains('xml')
    * eval karate.proceed()

Scenario: pathMatches('/cats') && methodIs('post')
    * eval karate.proceed()

Scenario: pathMatches('/cats/{id}') && acceptContains('xml')
    * eval karate.proceed()

Scenario: pathMatches('/cats/{id}')
    * eval karate.proceed()

Scenario: pathMatches('/cats/{id}/kittens')
    * eval karate.proceed()
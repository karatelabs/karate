Feature:

Background:
* def nextId = call read('increment.js')
* def cats = {}
* def targetUrlBase = 'http://127.0.0.1:' + karateMockPort
* print 'init target url:', targetUrlBase

Scenario: pathMatches('/greeting') && paramValue('name') != null
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/greeting')
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/cats') && methodIs('post') && typeContains('xml')
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/cats') && methodIs('post')
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/cats/{id}') && acceptContains('xml')
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/cats/{id}')
    * eval karate.proceed(targetUrlBase)

Scenario: pathMatches('/cats/{id}/kittens')
    * eval karate.proceed(targetUrlBase)
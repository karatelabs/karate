Feature: mock that simply forwards to actual host

Background:
* def nextId = call read('increment.js')
* def cats = {}
# if argument to karate.proceed() is null, url of incoming request is used (no url re-writing)
* def targetUrlBase = demoServerPort ? 'http://127.0.0.1:' + demoServerPort : null
* print 'init target url:', targetUrlBase

Scenario: pathMatches('/greeting') && paramExists('name', '')
    * karate.proceed(targetUrlBase)

# 'catch-all' rule
Scenario:
    * karate.proceed(targetUrlBase)

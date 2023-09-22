Feature: mock that simply forwards to actual host

Background:
* def cats = {}
# if argument to karate.proceed() is null, url of incoming request is used (no url re-writing)
* def targetUrlBase = demoServerPort ? 'http://127.0.0.1:' + demoServerPort : null
* print 'init target url:', targetUrlBase

Scenario: pathMatches('/greeting') && paramExists('name')
* print '*** param exists: name', targetUrlBase
* requestHeaders['host'] = 'myhost:123'
* karate.proceed(targetUrlBase)

# 'catch-all' rule
Scenario:
* karate.proceed(targetUrlBase)

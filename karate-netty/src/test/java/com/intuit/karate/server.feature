@ignore
Feature:

Background:
* def cats = {}
* def id = 0
* configure cors = true

Scenario: pathMatches('/v1/cats') && methodIs('post')
    * def cat = request
    * def id = ~~(id + 1)
    * cat.id = id
    * cats[id + ''] = cat
    * def response = cat

Scenario: pathMatches('/v1/cats') && methodIs('get')
    * def response = $cats.*

Scenario: pathMatches('/v1/cats/{id}') && methodIs('get')
    * def response = cats[pathParams.id]
    * def responseStatus = response ? 200 : 404

Scenario: pathMatches('/v1/body/json') && bodyPath('$.name') == 'Scooby'
    * def response = { success: true }

Scenario: pathMatches('/v1/body/xml') && bodyPath('/dog/name') == 'Scooby'
    * def response = { success: true }

Scenario: pathMatches('/v1/abort')
    * def response = { success: true }
    * if (response.success) karate.abort()
    # the next line will not be executed
    * def response = { success: false }

Scenario:
    * def responseStatus = 404
    * def responseHeaders = { 'Content-Type': 'text/html; charset=utf-8' }
    * def response = <html><body>Not Found</body></html>

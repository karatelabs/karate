Feature:

Background:
* def savedRequests = []

Scenario: pathMatches('/api/05')
* savedRequests.push({ path: requestPath, params: requestParams })
* print 'saved:', savedRequests
* def response = { message: 'hello faked' }

@ignore
Feature:

Background:
* configure cors = true
* def count = 0

Scenario: pathMatches('/api/{img}')
* def response = read('billie.jpg')
* def responseHeaders = { 'Content-Type': 'image/jpeg' }

Scenario:
* def count = count + 1
* def lastName = 'Request #' + count
* def response = read('response.json')

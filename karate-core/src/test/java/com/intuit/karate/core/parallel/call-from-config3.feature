Feature:
Background:
  * url 'http://localhost:' + karate.properties['server.port']

Scenario: reproducing #1835
  * call read('parallel-outline-call-api.feature')
  * def headers = response.headers

Feature:
Background:
  * url 'http://localhost:' + karate.properties['server.port']

Scenario: reproducing #1835
  # https://github.com/karatelabs/karate/issues/1835#issuecomment-969471445
  * call read('parallel-outline-call-api.feature') [{'key':'value'}, {'key':'value2'}]
  * def headers = response.headers
  * call read('parallel-outline-call-api.feature')
  * def headers = response.headers
  * def r = call read('parallel-outline-call-api.feature')
  * def headers = r.response.headers
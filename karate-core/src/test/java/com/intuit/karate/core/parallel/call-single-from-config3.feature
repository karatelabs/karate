Feature:
Background:
  * url serverUrl

Scenario: reproducing #1835
  * call read('parallel-outline-call-api.feature')
  * def headers = response.headers

Feature: dynamic params using scenario-outline, examples and json

Background:
* url demoBaseUrl
* def fix = function(v) { return v ? v : null }
* def getResponseParam = read('get-response-param.js')

Scenario Outline: different combinations of search params

* def query = { name: '#(fix("<name>"))', country: '#(fix("<country>"))', active: '#(fix("<active>"))', limit: '#(fix(<limit>))' }
* print 'query: ' + query

Given path 'search'
# the 'params' keyword takes json, and will ignore any key that has a null value
And params query
When method get
Then status 200
And assert getResponseParam('name') == query.name
And assert getResponseParam('country') == query.country
And assert getResponseParam('active') == query.active
And assert getResponseParam('limit') == query.limit

# response should NOT contain a key expected to be missing
And match response !contains { '<missing>': '#notnull' }

Examples:
| name | country | active | limit | missing |
| foo  | IN      | true   |     1 |         |
| bar  |         |        |     5 | country |
| bar  | JP      |        |       | active  |
|      | US      |        |     3 | name    |
|      |         | false  |       | limit   |

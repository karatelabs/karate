Feature: dynamic params using examples and json

Background:
* url demoBaseUrl
* def fix = function(v) { return v ? v : null }
* def getResponseParam = read('get-response-param.js')

Scenario Outline: different combinations of search params

    * def query = { name: '#(fix("<name>"))', country: '#(fix("<country>"))', active: '#(fix("<active>"))', limit: '#(fix(<limit>))' }
    * print 'query: ' + query
    
    Given path 'search'    
    And params query
    When method get
    Then status 200
    And assert getResponseParam('name') == query.name
    And assert getResponseParam('country') == query.country
    And assert getResponseParam('active') == query.active
    And assert getResponseParam('limit') == query.limit

Examples:
| name | country | active | limit |
| foo  | IN      | true   |     1 |
| bar  |         |        |     5 |
| bar  | JP      |        |       |
|      | US      |        |     3 |
|      |         | false  |       | 

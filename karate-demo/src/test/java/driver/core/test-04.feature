Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, showProcessLog: true }

  When driver webUrlBase + '/page-02'
  * def list = scriptAll('div#eg01 div', '_.textContent', function(x){ return x.contains('data2') })


Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
#| playwright |
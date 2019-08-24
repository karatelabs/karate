Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-03'

  # powerful wait designed for tabular results that take time to load
  When def list = waitForResultCount('div#eg01 div', 4)  
  Then match list == '#[4]'

  When def list = waitForResultCount('div#eg01 div', 4, '_.innerHTML')
  Then match list == '#[4]'
  And match each list contains '@@data'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
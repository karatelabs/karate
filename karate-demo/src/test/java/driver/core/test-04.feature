Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  Given driver webUrlBase + '/page-01'
  * script('1 + 2')

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
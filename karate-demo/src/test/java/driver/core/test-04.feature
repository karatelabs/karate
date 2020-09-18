Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, showProcessLog: true, playwrightUrl: 'ws://127.0.0.1:60168/97d3d6788b8e1063c0af0aae463f8d6d' }

  * driver webUrlBase + '/page-03'
  * def temp = text('#eg01Data1')
  * def temp = position('#eg01Data1')
  * print temp
  * screenshot()

Examples:
| type         |
#| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
| playwright |
Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, showProcessLog: true }

  * driver webUrlBase + '/page-03'
  * match driver.cookies == '#[]'
  * def temp = locate('#eg01Data1')
  * temp.parent.highlight()
  * def list = temp.parent.children
  * list[3].highlight()

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
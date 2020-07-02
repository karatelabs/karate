Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-03'
  * def temp = locate('#eg01Data1')
  * temp.parent.highlight()
  * def list = temp.parent.children
  * list[3].highlight()
  # * karate.stop(9000)

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'

* mouse('{}Double Click').doubleClick()
* match text('#eg03Result') == 'DOUBLE'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'
  * click('{a}Click Me')
  * match text('#eg03Result') == 'A'
  * click('{^span}Me')
  * match text('#eg03Result') == 'SPAN'
  * click('{div}Click Me')
  * match text('#eg03Result') == 'DIV'
  * click('{^div:2}Click')
  * match text('#eg03Result') == 'SECOND'
  * click('{span/a}Click Me')
  * match text('#eg03Result') == 'NESTED'
  * click('{:4}Click Me')
  * match text('#eg03Result') == 'BUTTON'
  * click("{^button:2}Item")
  * match text('#eg03Result') == 'ITEM2'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
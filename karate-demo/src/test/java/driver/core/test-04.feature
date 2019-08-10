Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'
  
  # wildcard locators
  * click('^Click Me')
  * match text('#eg03Result') == 'A'
  * click('^{span}Click Me')
  * match text('#eg03Result') == 'SPAN'
  * click('^{div}Click Me')
  * match text('#eg03Result') == 'DIV'
  * click('^{div:1}Click Me')
  * match text('#eg03Result') == 'SECOND'
  * click('^{span/a}Click Me')
  * match text('#eg03Result') == 'NESTED'
  * click('^{:3}Click Me')
  * match text('#eg03Result') == 'BUTTON'


Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
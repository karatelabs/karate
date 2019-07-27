Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-01'
  And match driver.location contains 'page-01'
  Then driver.location = webUrlBase + '/page-01'
  And input('#eg02InputId', Key.SHIFT)
  Then match text('#eg02DivId') == '16'

Examples:
  | type         |
  | chrome       |
  | chromedriver |
  | geckodriver  |
  | safaridriver |
Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-01'
  And driver.input('#eg02InputId', Key.SHIFT)
  Then match driver.text('#eg02DivId') == '16'


Examples:
  | type         |
  | chrome       |
  | chromedriver |
  | geckodriver  |
  | safaridriver |
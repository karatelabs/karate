Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-03'
  * def list = driver.values("input[name='data2']")
  * match list == '#[3]'
  * match each list contains 'check'


Examples:
| type         |
  | chrome       |
  | chromedriver |
  | geckodriver  |
  | safaridriver |
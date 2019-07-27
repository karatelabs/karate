Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-03'
  * def dims = driver.dimensions


Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-01'

  * driver.http.path('url').post({ url: 'https://github.com' })
  * delay(3000)
  

Examples:
| type         |
#| chrome       |
| chromedriver |
#| geckodriver  |
#| safaridriver |
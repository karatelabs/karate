Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'
  * script("sessionStorage.setItem('foo', 'bar')")
  * match script("sessionStorage.getItem('foo')") == 'bar'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
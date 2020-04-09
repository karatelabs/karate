Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-01'

  * def temp = locate('#eg01').locateAll('input')
  * karate.forEach(temp, function(x, i){ karate.log(i, x.html) })

Examples:
| type         |
# | chrome       |
| chromedriver |
#| geckodriver  |
#| safaridriver |
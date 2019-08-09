Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-04'
  * mouse().move('#eg02LeftDivId').perform()
  * mouse().move('#eg02RightDivId').click().perform()
  * mouse().down().move('#eg02LeftDivId').up().perform()
  * def temp = text('#eg02ResultDivId')
  * match temp contains 'LEFT_HOVERED'
  * match temp contains 'RIGHT_CLICKED'
  * match temp !contains 'LEFT_DOWN'
  * match temp contains 'LEFT_UP'

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  Given driver webUrlBase + '/page-01'

  # key events and key combinations
  And input('#eg02InputId', Key.CONTROL + 'a')
  And def temp = text('#eg02DivId')
  And match temp contains '17d'
  And match temp contains '65u'

Examples:
| type         |
#| chrome       |
| chromedriver |
#| geckodriver  |
#| safaridriver |
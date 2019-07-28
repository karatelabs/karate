Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-01'
  And input('#eg02InputId', Key.CONTROL + 'a')
  Then match text('#eg02DivId') contains (type == 'geckodriver' ? '17d17u65d' : '17d65d')

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
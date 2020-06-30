Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, start: false, port: 9222, attach: 'reddit' }

        * driver null
        * highlightAll('a')
        * karate.stop(9000)

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
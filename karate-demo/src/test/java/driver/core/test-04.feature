Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

        Given driver 'https://google.com'
        When input('input[name=q]', 'karate dsl' + Key.ENTER)
        Then waitFor('{h3}intuit/karate: Test Automation Made Simple - GitHub')

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
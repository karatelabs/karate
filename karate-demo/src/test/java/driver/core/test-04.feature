Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-04'
  And match driver.location == webUrlBase + '/page-04'
  And switchFrame('#frame01')
  When input('#eg01InputId', 'hello world')
  And click('#eg01SubmitId')
  Then match text('#eg01DivId') == 'hello world'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |
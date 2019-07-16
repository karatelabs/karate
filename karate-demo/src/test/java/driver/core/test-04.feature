Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-04'
  And match driver.location == webUrlBase + '/page-04'
  And driver.switchFrame(0)
  When driver.input('#eg01InputId', 'hello world')
  And driver.click('#eg01SubmitId')
  Then match driver.text('#eg01DivId') == 'hello world'

  # switch back to parent frame
  When driver.switchFrame(null)
  Then match driver.text('#eg01DivId') == 'this div is outside the iframe'

  # switch to iframe by locator
  Given driver webUrlBase + '/page-04'
  And match driver.location == webUrlBase + '/page-04'
  And driver.switchFrame('#frame01')
  When driver.input('#eg01InputId', 'hello world')
  And driver.click('#eg01SubmitId')
  Then match driver.text('#eg01DivId') == 'hello world'

Examples:
| type         |
 | chrome       |
 | chromedriver |
 | geckodriver  |
 # | safaridriver |
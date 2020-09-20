Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, showProcessLog: true }

  Given driver webUrlBase + '/page-04'
  And match driver.url == webUrlBase + '/page-04'
  # TODO problem with safari
  And switchFrame(type == 'safaridriver' ? '#frame01' : 0)
  When input('#eg01InputId', 'hello world')
  And click('#eg01SubmitId')
  Then match text('#eg01DivId') == 'hello world'

  # switch back to parent frame
  When switchFrame(null)
  Then match text('#eg01DivId') == 'this div is outside the iframe'

  # switch to iframe by locator
  Given driver webUrlBase + '/page-04'
  And match driver.url == webUrlBase + '/page-04'
  And switchFrame(type == 'playwright' ? 'frame01' : '#frame01')
  When input('#eg01InputId', 'hello world')
  And click('#eg01SubmitId')
  Then match text('#eg01DivId') == 'hello world'
  And switchFrame(null)
  # mouse move and click
  * mouse('#eg02LeftDivId').go()
  * mouse('#eg02RightDivId').click()
  * mouse().down().move('#eg02LeftDivId').up()
  * def temp = text('#eg02ResultDivId')
  # works only for chrome :(
  * match temp contains 'LEFT_HOVERED'
  * match temp contains 'RIGHT_CLICKED'
  * match temp !contains 'LEFT_DOWN'
  * match temp contains 'LEFT_UP'


Examples:
| type         |
#| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
| playwright |
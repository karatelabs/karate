Feature: ui automation core capabilities

Background:
  * configure driver = { type: 'chrome', timeOut: 5000, executable: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }
  * def webUrlBase = karate.properties['web.url.base']

Scenario: dom operations and validations
  Given location webUrlBase + '/page-01'
  And input #eg01InputId = 'hello world'
  When click input[name=eg01SubmitName]
  And match driver.text('#eg01DivId') == 'hello world'
  
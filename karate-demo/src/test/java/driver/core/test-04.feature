Feature: scratch pad 2

Scenario:
  * configure driver = { type: 'chrome', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-04'
  * match driver.location == webUrlBase + '/page-04'
  * driver.switchFrame('#frame01')
  * driver.input('#eg01InputId', 'hello world')
  * driver.click('#eg01SubmitId')
  * match driver.text('#eg01DivId') == 'hello world'

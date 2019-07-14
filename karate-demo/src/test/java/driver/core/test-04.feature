Feature: scratch pad 2

Scenario:
  * configure driver = { type: 'safaridriver', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-04'
  * match driver.location == webUrlBase + '/page-04'
  * driver.switchFrame('#frame01')
  * input('#eg01InputId', 'hello world')
  * click('#eg01SubmitId')
  * match text('#eg01DivId') == 'hello world'
  * driver.switchFrame(null)
  * match text('#eg01DivId') == 'this div is outside the iframe'

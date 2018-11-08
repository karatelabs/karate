Feature: scratch pad

Scenario:
  * configure driver = { type: 'chromedriver', start: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-01'
  * assert driver.eval('1 + 2') == 3
  * match driver.eval("location.href") == webUrlBase + '/page-01'

    
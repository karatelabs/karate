Feature: scratch pad

Scenario:
  * configure driver = { type: 'chrome', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-03'
  * assert driver.eval('1 + 2') == 3
  * match driver.eval("location.href") == webUrlBase + '/page-03'
  * def getSubmitFn = function(formId){ return "document.getElementById('" + formId + "').submit()" }
  * driver.eval(getSubmitFn('eg02FormId'))

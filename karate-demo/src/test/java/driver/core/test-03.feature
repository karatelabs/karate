Feature: scratch pad 1

Scenario:
  * configure driver = { type: 'chrome', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-03'
  * match script('1 + 2') == 3
  * match script("location.href") == webUrlBase + '/page-03'
  * def getSubmitFn = function(formId){ return "document.getElementById('" + formId + "').submit()" }
  * script(getSubmitFn('eg02FormId'))

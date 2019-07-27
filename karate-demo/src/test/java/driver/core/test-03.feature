Feature: scratch pad 1

Scenario:
  * configure driver = { type: 'chrome', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-03'
  * match evaluate('1 + 2') == 3
  * match evaluate("location.href") == webUrlBase + '/page-03'
  * def getSubmitFn = function(formId){ return "document.getElementById('" + formId + "').submit()" }
  * evaluate(getSubmitFn('eg02FormId'))

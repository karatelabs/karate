Feature: axe accessibility native

  Background:
    * configure driver = { type: 'chrome' }

  Scenario:
    # get axe script
    * def axeJs = karate.http('https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.7.0/axe.min.js').get().body
    * driver 'https://dequeuniversity.com/demo/dream'
    * waitFor('.submit-search')
    # inject axe script
    * driver.script(axeJs)
    # execute axe
    * def axeResponse = driver.scriptAwait('axe.run()')
    * karate.write(axeResponse, 'axe-response.json')
    * doc { read: 'axe-report.html' }    
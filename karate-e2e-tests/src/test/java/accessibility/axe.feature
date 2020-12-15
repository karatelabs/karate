Feature: axe accessibility native

  Background:
    * configure driver = { type: 'chrome' }

  Scenario:
    # get axe script
    * def axeJs = karate.http('https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.0.2/axe.min.js').get().body
    * driver 'https://www.seleniumeasy.com/test/dynamic-data-loading-demo.html'
    # inject axe script
    * driver.script(axeJs);
    # execute axe
    * def axeResponse = driver.scriptAwaitPromise('axe.run().then(results => JSON.stringify(results))');
    * doc { read: 'axe-report.html' }    
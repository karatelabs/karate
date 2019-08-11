Feature: browser automation 2

  Background:
  * configure driver = { type: 'chrome', showDriverLog: true }
  # * configure driverTarget = { docker: 'justinribeiro/chrome-headless', showDriverLog: true }
  # * configure driverTarget = { docker: 'ptrthomas/karate-chrome', showDriverLog: true }
  # * configure driver = { type: 'chromedriver', showDriverLog: true }
  # * configure driver = { type: 'geckodriver', showDriverLog: true }
  # * configure driver = { type: 'safaridriver', showDriverLog: true }

  Scenario: google search, land on the karate github, and search for a file

    Given driver 'https://google.com'
    And input('input[name=q]', 'karate dsl')
    When click('input[name=btnI]')
    Then waitForUrl('https://github.com/intuit/karate')

    When click('{a}Find File')
    And def search = waitFor('input[name=query]')
    Then match driver.url == 'https://github.com/intuit/karate/find/master'

    Given search.input('karate-logo.png')
    And def fun = function(){ var res = scripts('.js-tree-browser-result-path', '_.innerText'); return res.size() == 2 ? res : null }
    And def results = waitUntil(fun)
    And match results contains 'karate-core/src/main/resources/karate-logo.png'

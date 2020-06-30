Feature: 3 scenarios

  Background:
    # * configure driver = { type: 'chromedriver', showDriverLog: true }
    * configure driverTarget = { docker: 'ptrthomas/karate-chrome', showDriverLog: true }

  Scenario: try to login to github
  and then do a google search

    Given driver 'https://github.com/login'
    And input('#login_field', 'dummy')
    And input('#password', 'world')
    When submit().click("input[name=commit]")
    Then match html('#js-flash-container') contains 'Incorrect username or password.'

    Given driver 'https://google.com'
    And input("input[name=q]", 'karate dsl')
    When submit().click("input[name=btnI]")
    Then match driver.url == 'https://github.com/intuit/karate'

  Scenario: google search, land on the karate github page, and search for a file

    Given driver 'https://google.com'
    And input('input[name=q]', 'karate dsl')
    When click('input[name=btnI]')
    Then waitForUrl('https://github.com/intuit/karate')

    When click('{a}Go to file')
    And def searchField = waitFor('input[name=query]')
    Then match driver.url == 'https://github.com/intuit/karate/find/master'

    When searchField.input('karate-logo.png')
    Then def searchResults = waitForResultCount('.js-tree-browser-result-path', 2, '_.innerText')
    Then match searchResults contains 'karate-core/src/main/resources/karate-logo.png'

  Scenario: test-automation challenge
    Given driver 'https://semantic-ui.com/modules/dropdown.html'
    And def locator = "select[name=skills]"
    Then scroll(locator)
    And click(locator)
    And click('div[data-value=css]')
    And click('div[data-value=html]')
    And click('div[data-value=ember]')
    And delay(1000)
Feature: 3 scenarios

  Background:
    * configure driver = { type: 'chrome', showDriverLog: true }
    # * configure driverTarget = { docker: 'ptrthomas/karate-chrome', showDriverLog: true }

  Scenario: test-automation challenge
    Given driver 'https://semantic-ui.com/modules/dropdown.html'
    And def locator = "select[name=skills]"
    Then scroll(locator)
    And click(locator)
    And click('div[data-value=css]')
    And click('div[data-value=html]')
    And click('div[data-value=ember]')
    And delay(1000)
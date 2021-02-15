Feature: parallel testing demo - single node using docker

  Background:
    * configure driverTarget = { docker: 'ptrthomas/karate-chrome' }
    # * configure driver = { type: 'chrome', start: false }

  Scenario: attempt github login
    * driver 'https://github.com/login'
    * input('#login_field', 'dummy')
    * input('#password', 'world')
    * submit().click("input[name=commit]")
    * match html('#js-flash-container') contains 'Incorrect username or password.'

  Scenario: google search for karate
    Given driver 'https://google.com'
    And input("input[name=q]", 'karate dsl')
    When submit().click("input[name=btnI]")
    Then waitForUrl('https://github.com/intuit/karate')

  Scenario: test automation tool challenge
    * driver 'https://semantic-ui.com/modules/dropdown.html'
    * scroll('select[name=skills]').click()
    * click('div[data-value=css]')
    * click('div[data-value=html]')
    * click('div[data-value=ember]')
    * delay(1000).screenshot()

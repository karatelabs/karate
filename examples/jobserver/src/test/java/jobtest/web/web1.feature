Feature: web 1

  Scenario: try to login to github
  and then do a google search

    Given driver 'https://github.com/login'
    And input('#login_field', 'dummy')
    And input('#password', 'world')
    When submit().click("input[name=commit]")
    Then match html('.flash-error') contains 'Incorrect username or password.'

    Given driver 'https://google.com'
    And input("input[name=q]", 'karate dsl')
    When submit().click("input[name=btnI]")
    Then waitForUrl('https://github.com/intuit/karate')

  Scenario: test-automation challenge
    Given driver 'https://semantic-ui.com/modules/dropdown.html'
    And def locator = "select[name=skills]"
    Then scroll(locator)
    And click(locator)
    And click('div[data-value=css]')
    And click('div[data-value=html]')
    And click('div[data-value=ember]')
    And delay(1000)
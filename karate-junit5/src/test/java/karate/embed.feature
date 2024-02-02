Feature: browser automation demo

  Background:
    * configure driver = { type: 'chrome' }
  @ignore
  Scenario: try to login to github and then do a google search
    Simulates step failure for testing embedded screenshots. Remove @ignore for developement

    Given driver 'https://github.com/login'
    And input('#login_field', 'YYY')
    And input('#password', 'world')
    When submit().click("input[name=commit]")
    Then match html('.flash-error') contains 'Bad username or password.'

    Given driver 'https://google.com'
    # And click('{}Accept all')
    And input("[name=q][name=q]", 'karate dsl')
    When submit().click("input[name=btnI]")
    Then waitForUrl('https://github.com/karatelabs/karate')

   Scenario: Dummy not to fail build
    * print 'dummy'
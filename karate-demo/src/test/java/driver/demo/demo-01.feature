Feature: browser automation

Background:
  # configure driver = { type: 'chrome', start: false, showDriverLog: true }
  # configure driverTarget = { docker: 'justinribeiro/chrome-headless', showDriverLog: true }
  # configure driverTarget = { docker: 'ptrthomas/karate-chrome:0.9.5.RC', showDriverLog: true }
  # * configure driver = { type: 'chromedriver', showDriverLog: true }
  * configure driver = { type: 'geckodriver', showDriverLog: true }
  # * configure driver = { type: 'safaridriver' }
  # * configure driver = { type: 'mswebdriver' }

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
  Then match driver.location == 'https://github.com/intuit/karate'
  And screenshot()

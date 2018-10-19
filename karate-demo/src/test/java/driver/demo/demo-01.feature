@ignore
Feature: browser automation

Background:
  * configure driver = { type: 'chrome', start: true }
  # * configure driver = { type: 'chromedriver', start: true }
  # * configure driver = { type: 'geckodriver', start: true }  
  # * configure driver = { type: 'safaridriver', start: true }
  # * configure driver = { type: 'mswebdriver', start: true }

Scenario: try to login to github
    and then do a google search

  Given location 'https://github.com/login'
  And input #login_field = 'hello'
  And input #password = 'world'
  When submit //input[@name='commit']
  Then match driver.html('#js-flash-container') contains 'Incorrect username or password.'
  
  Given location 'https://google.com'
  And input //input[@name='q'] = 'karate dsl'
  When submit //input[@name='btnI']
  Then match driver.location == 'https://github.com/intuit/karate'
   